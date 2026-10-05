import { app, BrowserWindow, dialog, ipcMain, Menu, session, shell, type IpcMainInvokeEvent, type MenuItemConstructorOptions } from 'electron';
import { join } from 'node:path';
import { billingReturnPath, contentSecurityPolicy, externalUrl, serviceOrigin, stripeUrl } from './policy.cjs';
import { startDesktopServer } from './server.cjs';

app.setName('MicroCrew');
let mainWindow: BrowserWindow | null = null;
let paymentWindow: BrowserWindow | null = null;
let paymentUrl = '';
let appOrigin = '';
let server: Awaited<ReturnType<typeof startDesktopServer>> | undefined;
let quitting = false;

function trustedRenderer(event: IpcMainInvokeEvent) {
  return mainWindow && !mainWindow.isDestroyed() && event.sender === mainWindow.webContents &&
    event.senderFrame === mainWindow.webContents.mainFrame && new URL(event.senderFrame.url).origin === appOrigin;
}

function returnToBilling(path: string) {
  if (!mainWindow || mainWindow.isDestroyed() || quitting) return;
  mainWindow.webContents.send('microcrew:billing-return', path);
  if (mainWindow.isMinimized()) mainWindow.restore();
  mainWindow.show(); mainWindow.focus();
}

async function openStripe(value: unknown) {
  const url = stripeUrl(value);
  if (paymentWindow && !paymentWindow.isDestroyed()) {
    if (url !== paymentUrl) { paymentUrl = url; await paymentWindow.loadURL(url); }
    paymentWindow.focus(); return;
  }
  const returnOrigins = new Set([appOrigin, 'http://127.0.0.1:5173', 'http://localhost:5173',
    serviceOrigin(process.env.MICROCREW_BILLING_RETURN_ORIGIN || 'http://127.0.0.1:5173')]);
  const paymentSession = session.fromPartition('microcrew-stripe');
  paymentSession.setPermissionRequestHandler((_contents, _permission, callback) => callback(false));
  paymentSession.setPermissionCheckHandler(() => false);
  const window = new BrowserWindow({
    title: 'MicroCrew — Stripe TEST', width: 1040, height: 820, minWidth: 600, minHeight: 600,
    parent: mainWindow || undefined, show: false, backgroundColor: '#ffffff',
    webPreferences: { session: paymentSession, sandbox: true, contextIsolation: true, nodeIntegration: false, webSecurity: true, webviewTag: false, devTools: !app.isPackaged },
  });
  paymentWindow = window;
  paymentUrl = url;
  window.setMenu(null);
  let returned = false;
  const route = (event: Electron.Event, target: string) => {
    const path = billingReturnPath(target, returnOrigins);
    if (path) {
      event.preventDefault(); returned = true;
      returnToBilling(path);
      window.close();
      return;
    }
    try {
      const next = new URL(target);
      if (next.protocol !== 'https:' || !['checkout.stripe.com', 'billing.stripe.com', 'hooks.stripe.com'].includes(next.hostname) ||
          next.username || next.password || (next.port && next.port !== '443')) event.preventDefault();
    } catch { event.preventDefault(); }
  };
  window.webContents.on('will-navigate', route);
  window.webContents.on('will-redirect', route);
  window.webContents.setWindowOpenHandler(({ url: target }) => {
    try {
      const safe = externalUrl(target);
      if (['stripe.com', 'docs.stripe.com'].includes(new URL(safe).hostname)) void shell.openExternal(safe);
    } catch { /* Never forward file, javascript, or custom protocols. */ }
    return { action: 'deny' };
  });
  window.once('ready-to-show', () => window.show());
  window.on('closed', () => {
    paymentWindow = null;
    if (!returned) returnToBilling(new URL(paymentUrl).hostname === 'checkout.stripe.com' ? '/billing?checkout=cancelled' : '/billing');
  });
  try { await window.loadURL(url); }
  catch { if (!window.isDestroyed()) window.close(); throw new Error('Nu s-a putut încărca Stripe. Verifică conexiunea la internet.'); }
}

function createWindow() {
  const window = new BrowserWindow({
    title: 'MicroCrew', width: 1280, height: 860, minWidth: 840, minHeight: 620,
    show: false, backgroundColor: '#ffffff', icon: join(app.getAppPath(), 'build/icon.png'),
    webPreferences: { preload: join(__dirname, 'preload.cjs'), sandbox: true, contextIsolation: true,
      nodeIntegration: false, webSecurity: true, webviewTag: false, devTools: !app.isPackaged, navigateOnDragDrop: false },
  });
  mainWindow = window;
  window.webContents.on('will-navigate', (event, target) => {
    try { if (new URL(target).origin !== appOrigin) event.preventDefault(); }
    catch { event.preventDefault(); }
  });
  window.webContents.on('will-redirect', (event, target) => {
    try { if (new URL(target).origin !== appOrigin) event.preventDefault(); }
    catch { event.preventDefault(); }
  });
  window.webContents.on('will-attach-webview', event => event.preventDefault());
  window.webContents.setWindowOpenHandler(({ url }) => {
    try { void shell.openExternal(externalUrl(url)).catch(() => {}); } catch { /* Invalid protocols stay blocked. */ }
    return { action: 'deny' };
  });
  window.once('ready-to-show', () => window.show());
  window.on('closed', () => { mainWindow = null; paymentWindow?.close(); });
  void window.loadURL(appOrigin).catch(() => dialog.showErrorBox('MicroCrew', 'Nu s-a putut încărca interfața locală. Repornește aplicația.'));
}

function installMenu() {
  const view: MenuItemConstructorOptions[] = [{ role: 'reload' }, { role: 'forceReload' }, { type: 'separator' },
    { role: 'resetZoom' }, { role: 'zoomIn' }, { role: 'zoomOut' }, { type: 'separator' }, { role: 'togglefullscreen' }];
  if (!app.isPackaged) view.push({ type: 'separator' }, { role: 'toggleDevTools' });
  const template: MenuItemConstructorOptions[] = [
    ...(process.platform === 'darwin' ? [{ role: 'appMenu' as const }] : []),
    { label: 'Fișier', submenu: [process.platform === 'darwin' ? { role: 'close' } : { role: 'quit' }] },
    { label: 'Editare', submenu: [{ role: 'undo' }, { role: 'redo' }, { type: 'separator' }, { role: 'cut' }, { role: 'copy' }, { role: 'paste' }, { role: 'selectAll' }] },
    { label: 'Vizualizare', submenu: view }, { role: 'windowMenu' },
  ];
  Menu.setApplicationMenu(Menu.buildFromTemplate(template));
}

if (!app.requestSingleInstanceLock()) app.quit();
else {
  app.on('second-instance', () => { if (mainWindow) { if (mainWindow.isMinimized()) mainWindow.restore(); mainWindow.show(); mainWindow.focus(); } });
  app.whenReady().then(async () => {
    if (!app.isPackaged && process.env.MICROCREW_DEV_URL) {
      appOrigin = serviceOrigin(process.env.MICROCREW_DEV_URL);
      if (!appOrigin.startsWith('http://127.0.0.1:')) throw new Error('Serverul Vite desktop trebuie să ruleze pe 127.0.0.1.');
    } else {
      server = await startDesktopServer(join(app.getAppPath(), 'dist'), process.env.MICROCREW_BACKEND_URL || 'http://127.0.0.1:8080');
      appOrigin = server.origin;
    }
    session.defaultSession.setPermissionRequestHandler((_contents, _permission, callback) => callback(false));
    session.defaultSession.setPermissionCheckHandler(() => false);
    session.defaultSession.webRequest.onHeadersReceived({ urls: [appOrigin + '/*'] }, (details, callback) => {
      callback({ responseHeaders: { ...details.responseHeaders, 'Content-Security-Policy': [contentSecurityPolicy(appOrigin)] } });
    });
    ipcMain.handle('microcrew:open-stripe', (event, value: unknown) => {
      if (!trustedRenderer(event)) throw new Error('Fereastră neautorizată.');
      return openStripe(value);
    });
    ipcMain.handle('microcrew:open-external', (event, value: unknown) => {
      if (!trustedRenderer(event)) throw new Error('Fereastră neautorizată.');
      return shell.openExternal(externalUrl(value));
    });
    if (process.platform === 'darwin') app.dock?.setIcon(join(app.getAppPath(), 'build/icon.png'));
    installMenu(); createWindow();
    app.on('activate', () => { if (!mainWindow) createWindow(); });
  }).catch(error => { dialog.showErrorBox('MicroCrew nu a pornit', error instanceof Error ? error.message : 'Eroare de pornire.'); app.quit(); });
  app.on('window-all-closed', () => { if (process.platform !== 'darwin') app.quit(); });
  app.on('before-quit', () => { quitting = true; void server?.close(); });
}
