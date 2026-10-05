import { contextBridge, ipcRenderer } from 'electron';

// The renderer receives only these narrow operations, never ipcRenderer or Node APIs.
contextBridge.exposeInMainWorld('microcrewDesktop', {
  platform: process.platform,
  openStripe: (url: string) => ipcRenderer.invoke('microcrew:open-stripe', url),
  openExternal: (url: string) => ipcRenderer.invoke('microcrew:open-external', url),
  onBillingReturn: (callback: (path: string) => void) => {
    const listener = (_event: Electron.IpcRendererEvent, path: unknown) => {
      if (typeof path === 'string' && /^\/billing(?:\?|$)/.test(path)) callback(path);
    };
    ipcRenderer.on('microcrew:billing-return', listener);
    return () => ipcRenderer.removeListener('microcrew:billing-return', listener);
  },
});
