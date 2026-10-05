import { before, after, test } from 'node:test';
import assert from 'node:assert/strict';
import { createServer, request } from 'node:http';
import { createHash } from 'node:crypto';
import { mkdtemp, mkdir, writeFile, rm } from 'node:fs/promises';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import { startDesktopServer } from '../dist-electron/server.cjs';
import { billingReturnPath, externalUrl, serviceOrigin, stripeUrl } from '../dist-electron/policy.cjs';

let directory, desktop, backend, backendOrigin;
let calls = 0, socketOrigin = 'unset';
const backendSockets = new Set();
const listen = server => new Promise(resolve => server.listen(0, '127.0.0.1', () => resolve(`http://127.0.0.1:${server.address().port}`)));

before(async () => {
  directory = await mkdtemp(join(tmpdir(), 'microcrew-desktop-'));
  await mkdir(join(directory, 'assets'));
  await writeFile(join(directory, 'index.html'), '<!doctype html><title>MicroCrew fixture</title><main>App</main>');
  await writeFile(join(directory, 'assets', 'app.css'), 'body { color: blue; }');
  backend = createServer((req, res) => {
    calls++;
    let body = '';
    req.on('data', chunk => { body += chunk; });
    req.on('end', () => {
      res.writeHead(201, { 'Content-Type': 'application/json' });
      res.end(JSON.stringify({ path: req.url, method: req.method, origin: req.headers.origin || null, auth: req.headers.authorization || null, body }));
    });
  });
  backend.on('connection', socket => { backendSockets.add(socket); socket.on('close', () => backendSockets.delete(socket)); });
  backend.on('upgrade', (req, socket) => {
    socketOrigin = req.headers.origin || null;
    const accept = createHash('sha1').update(req.headers['sec-websocket-key'] + '258EAFA5-E914-47DA-95CA-C5AB0DC85B11').digest('base64');
    socket.write(`HTTP/1.1 101 Switching Protocols\r\nUpgrade: websocket\r\nConnection: Upgrade\r\nSec-WebSocket-Accept: ${accept}\r\n\r\n`);
    socket.on('data', chunk => socket.write('echo:' + chunk));
  });
  backendOrigin = await listen(backend);
  desktop = await startDesktopServer(directory, backendOrigin);
});

after(async () => {
  await desktop?.close();
  backendSockets.forEach(socket => socket.destroy());
  if (backend?.listening) await new Promise(resolve => backend.close(resolve));
  await rm(directory, { recursive: true, force: true });
});

test('packaged routes reload as SPA and static assets retain MIME/CSP', async () => {
  const page = await fetch(desktop.origin + '/projects/123');
  assert.equal(page.status, 200);
  assert.match(await page.text(), /MicroCrew fixture/);
  assert.match(page.headers.get('content-security-policy'), /script-src 'self'/);
  const asset = await fetch(desktop.origin + '/assets/app.css');
  assert.equal(asset.headers.get('content-type'), 'text/css; charset=utf-8');
  assert.equal(asset.headers.get('x-content-type-options'), 'nosniff');
  assert.match(await asset.text(), /color: blue/);
  assert.equal((await fetch(desktop.origin + '/assets/missing.js')).status, 404);
});

test('API proxy forwards JWT, method, query, body and status to Spring without desktop Origin', async () => {
  const result = await fetch(desktop.origin + '/api/projects?size=12', {
    method: 'POST', headers: { Authorization: 'Bearer fixture-token', Origin: desktop.origin, 'Content-Type': 'application/json' },
    body: JSON.stringify({ title: 'Desktop project' }),
  });
  assert.equal(result.status, 201);
  assert.deepEqual(await result.json(), { path: '/api/projects?size=12', method: 'POST', origin: null, auth: 'Bearer fixture-token', body: '{"title":"Desktop project"}' });
});

test('foreign web origins and DNS rebinding hosts cannot use the local proxy', async () => {
  const count = calls;
  assert.equal((await fetch(desktop.origin + '/api/projects', { headers: { Origin: 'https://evil.example' } })).status, 403);
  // Fetch normalizes Host; use the raw HTTP client to exercise rebinding protection.
  const status = await new Promise((resolve, reject) => {
    const req = request(desktop.origin + '/api/projects', { headers: { Host: 'evil.example' } }, res => { res.resume(); resolve(res.statusCode); });
    req.on('error', reject); req.end();
  });
  assert.equal(status, 403);
  assert.equal(calls, count);
});

test('encoded traversal and invalid escapes cannot read outside packaged assets', async () => {
  assert.equal((await fetch(desktop.origin + '/%2e%2e%2fsecret.txt')).status, 403);
  assert.equal((await fetch(desktop.origin + '/%zz')).status, 400);
  assert.equal((await fetch(desktop.origin + '/%5c..%5csecret.txt')).status, 400);
});

function upgrade(origin) {
  return new Promise((resolve, reject) => {
    const req = request(desktop.origin + '/ws', { headers: {
      Connection: 'Upgrade', Upgrade: 'websocket', Origin: origin,
      'Sec-WebSocket-Version': '13', 'Sec-WebSocket-Key': Buffer.from('microcrew-ws-1234').toString('base64'),
    } });
    req.on('error', reject);
    req.setTimeout(3000, () => { req.destroy(); reject(new Error('Upgrade timeout')); });
    req.on('response', res => { res.resume(); reject(new Error('Expected WebSocket upgrade')); });
    req.on('upgrade', (res, socket) => {
      socket.setTimeout(3000, () => { socket.destroy(); reject(new Error('Echo timeout')); });
      socket.once('data', data => { socket.destroy(); resolve({ status: res.statusCode, echo: data.toString() }); });
      socket.write('microcrew-ping');
    });
    req.end();
  });
}

test('WebSocket upgrade and bidirectional bytes reach the backend for chat', async () => {
  assert.deepEqual(await upgrade(desktop.origin), { status: 101, echo: 'echo:microcrew-ping' });
  assert.equal(socketOrigin, null);
  await assert.rejects(upgrade('https://evil.example'));
});

test('Stripe return is limited to trusted billing routes and TEST sessions', () => {
  const origins = new Set(['http://127.0.0.1:5173']);
  assert.equal(billingReturnPath('http://127.0.0.1:5173/billing?checkout=success&session_id=cs_test_verified123', origins), '/billing?checkout=success&session_id=cs_test_verified123');
  assert.equal(billingReturnPath('http://127.0.0.1:5173/billing?checkout=cancelled', origins), '/billing?checkout=cancelled');
  assert.equal(billingReturnPath('http://127.0.0.1:5173/billing', origins), '/billing');
  assert.equal(billingReturnPath('http://127.0.0.1:5173/billing?checkout=success&session_id=cs_live_fake', origins), '/billing');
  assert.equal(billingReturnPath('https://evil.example/billing?checkout=success&session_id=cs_test_verified123', origins), null);
  assert.equal(billingReturnPath('http://127.0.0.1:5173/admin', origins), null);
});

test('native URL operations reject arbitrary protocols, credentials and lookalike Stripe domains', () => {
  assert.equal(stripeUrl('https://checkout.stripe.com/c/pay/cs_test_example'), 'https://checkout.stripe.com/c/pay/cs_test_example');
  assert.equal(externalUrl('https://github.com/example/project'), 'https://github.com/example/project');
  for (const url of ['file:///tmp/secret', 'javascript:alert(1)', 'https://checkout.stripe.com.evil.example/', 'https://user@checkout.stripe.com/', 'https://billing.stripe.com:444/']) {
    assert.throws(() => stripeUrl(url));
  }
  for (const url of ['file:///tmp/secret', 'javascript:alert(1)', 'http://github.com/', 'https://user:password@github.com/']) assert.throws(() => externalUrl(url));
  assert.equal(serviceOrigin('http://127.0.0.1:8080'), 'http://127.0.0.1:8080');
  assert.equal(serviceOrigin('https://api.example.test'), 'https://api.example.test');
  assert.throws(() => serviceOrigin('http://api.example.test'));
  assert.throws(() => serviceOrigin('https://api.example.test/private'));
});

test('backend outage produces a controlled 503 while the interface remains available', async () => {
  backendSockets.forEach(socket => socket.destroy());
  await new Promise(resolve => backend.close(resolve));
  const result = await fetch(desktop.origin + '/api/projects');
  assert.equal(result.status, 503);
  assert.match((await result.json()).detail, /Backendul MicroCrew/);
  assert.equal((await fetch(desktop.origin + '/login')).status, 200);
});
