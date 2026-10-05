import { createServer } from 'vite';
import { spawn } from 'node:child_process';
import electron from 'electron';

const vite = await createServer({ server: { host: '127.0.0.1', port: 5174, strictPort: true } });
await vite.listen();
vite.printUrls();
const child = spawn(electron, ['.'], { stdio: 'inherit', env: { ...process.env, MICROCREW_DEV_URL: 'http://127.0.0.1:5174' } });
let stopping = false;
async function stop(code = 0) {
  if (stopping) return;
  stopping = true;
  if (child.exitCode === null) child.kill('SIGTERM');
  await vite.close();
  process.exit(code);
}
child.on('error', error => { console.error(error.message); void stop(1); });
child.on('exit', code => { void stop(code || 0); });
process.on('SIGINT', () => { void stop(); });
process.on('SIGTERM', () => { void stop(); });
