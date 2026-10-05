import { clearSession, getToken } from './auth';
export class ApiError extends Error { constructor(public status: number, message: string) { super(message); } }
export async function api<T>(path: string, method = 'GET', body?: unknown): Promise<T> {
  const token = getToken();
  if (!token) throw new ApiError(401, 'Intră în cont pentru a continua.');
  const controller = new AbortController(); const timer = setTimeout(() => controller.abort(), 20000);
  try {
    const response = await fetch(path.startsWith('/api/') ? path : `/api${path}`, { method, headers: { Authorization: `Bearer ${token}`, ...(body === undefined ? {} : { 'Content-Type': 'application/json' }) }, body: body === undefined ? undefined : JSON.stringify(body), signal: controller.signal });
    if (!response.ok) {
      if (response.status === 401) { clearSession(); window.dispatchEvent(new Event('session-expired')); }
      let message = ({400:'Verifică valorile completate.',401:'Sesiunea a expirat. Intră din nou în cont.',403:'Nu ai acces la această pagină.',404:'Nu am găsit informația cerută.',409:'Această acțiune a fost deja procesată.'} as Record<number,string>)[response.status] || 'Serverul nu poate procesa cererea. Încearcă din nou.';
      if(response.status < 500) { try { const error = await response.json(); if(typeof error.detail === 'string') message=error.detail; } catch {} }
      throw new ApiError(response.status, message);
    }
    if(response.status === 204) return undefined as T;
    return await response.json() as T;
  } catch (error) {
    if (controller.signal.aborted) throw new Error('Serverul nu a răspuns la timp. Încearcă din nou.');
    if (error instanceof TypeError) throw new Error('Nu se poate contacta backendul. Verifică dacă este pornit.');
    throw error;
  } finally { clearTimeout(timer); }
}
