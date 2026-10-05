import { createServer, type IncomingMessage, type ServerResponse } from 'node:http';
import { readFile, stat } from 'node:fs/promises';
import { extname, isAbsolute, relative, resolve, sep } from 'node:path';
import type { Socket } from 'node:net';
import httpProxy = require('http-proxy');
import { contentSecurityPolicy, serviceOrigin } from './policy.cjs';

const mime: Record<string, string> = {
  '.html': 'text/html; charset=utf-8', '.js': 'text/javascript; charset=utf-8', '.css': 'text/css; charset=utf-8',
  '.json': 'application/json', '.svg': 'image/svg+xml', '.png': 'image/png', '.jpg': 'image/jpeg',
  '.jpeg': 'image/jpeg', '.webp': 'image/webp', '.ico': 'image/x-icon', '.woff': 'font/woff', '.woff2': 'font/woff2',
};

export async function startDesktopServer(distDirectory: string, backendUrl: string) {
  const root = resolve(distDirectory);
  await stat(resolve(root, 'index.html')); // Fail clearly if the frontend has not been built.
  const target = serviceOrigin(backendUrl);
  const proxy = httpProxy.createProxyServer({ target, changeOrigin: true, ws: true, secure: true, proxyTimeout: 45000, timeout: 45000 });
  const sockets = new Set<Socket>();
  let origin = '';
  const allowed = (request: IncomingMessage) => request.headers.host === new URL(origin).host &&
    (!request.headers.origin || request.headers.origin === origin);
  const send = (response: ServerResponse, status: number, detail: string) => {
    response.writeHead(status, { 'Content-Type': 'application/json; charset=utf-8', 'Cache-Control': 'no-store' });
    response.end(JSON.stringify({ detail }));
  };
  proxy.on('error', (_error, _request, response) => {
    if ('writeHead' in response && !response.headersSent) send(response, 503, 'Backendul MicroCrew nu este disponibil. Pornește serverul Spring sau verifică adresa configurată.');
    else response.destroy();
  });
  const server = createServer((request, response) => {
    void (async () => {
      if (!allowed(request)) { send(response, 403, 'Origine locală invalidă.'); return; }
      const url = new URL(request.url || '/', origin);
      if (url.origin !== origin) { send(response, 400, 'Adresă invalidă.'); return; }
      if (url.pathname.startsWith('/api/')) {
        // Validate the renderer origin locally. Spring receives a server-to-server request,
        // so it does not need a CORS exception for this randomly allocated desktop port.
        delete request.headers.origin;
        proxy.web(request, response);
        return;
      }
      if (!['GET', 'HEAD'].includes(request.method || '')) { send(response, 405, 'Metodă invalidă.'); return; }
      const pathname = decodeURIComponent(url.pathname);
      if (pathname.includes('\\') || pathname.includes('\0')) { send(response, 400, 'Cale invalidă.'); return; }
      let file = resolve(root, '.' + pathname);
      const rel = relative(root, file);
      if (rel === '..' || rel.startsWith('..' + sep) || isAbsolute(rel)) { send(response, 403, 'Cale invalidă.'); return; }
      let exists = await stat(file).then(info => info.isFile()).catch(() => false);
      if (!exists && !extname(pathname)) { file = resolve(root, 'index.html'); exists = true; }
      if (!exists) { send(response, 404, 'Fișier inexistent.'); return; }
      response.writeHead(200, {
        'Content-Type': mime[extname(file)] || 'application/octet-stream',
        'Content-Security-Policy': contentSecurityPolicy(origin),
        'X-Content-Type-Options': 'nosniff', 'Referrer-Policy': 'no-referrer', 'Cache-Control': 'no-store',
      });
      response.end(request.method === 'HEAD' ? undefined : await readFile(file));
    })().catch(() => { if (!response.headersSent) send(response, 400, 'Cerere invalidă.'); else response.destroy(); });
  });
  server.on('connection', socket => { sockets.add(socket); socket.on('close', () => sockets.delete(socket)); });
  server.on('upgrade', (request, socket, head) => {
    let url: URL;
    try { url = new URL(request.url || '/', origin); } catch { socket.destroy(); return; }
    if (!allowed(request) || url.origin !== origin || url.pathname !== '/ws') { socket.destroy(); return; }
    delete request.headers.origin;
    proxy.ws(request, socket, head);
  });
  await new Promise<void>((resolveReady, reject) => {
    server.once('error', reject);
    server.listen(0, '127.0.0.1', () => {
      const address = server.address();
      if (!address || typeof address === 'string') { reject(new Error('Nu s-a putut deschide serverul local.')); return; }
      origin = `http://127.0.0.1:${address.port}`;
      server.removeListener('error', reject);
      resolveReady();
    });
  });
  return {
    origin,
    close: () => new Promise<void>(resolveClosed => {
      server.close(() => resolveClosed());
      sockets.forEach(socket => socket.destroy());
      proxy.close();
    }),
  };
}
