type Session = { token: string; expiresAt: number };
const storageKey = 'microcrew-session';
let session: Session | null = null;
try { const value = JSON.parse(sessionStorage.getItem(storageKey) || 'null'); if (value && typeof value.token === 'string' && typeof value.expiresAt === 'number') session = value; } catch { sessionStorage.removeItem(storageKey); }
export function clearSession() { session = null; sessionStorage.removeItem(storageKey); }
export function currentUser(): {id:number;email:string} | null {
  const token = getToken(); if (!token) return null;
  try { const part = token.split('.')[1].replace(/-/g,'+').replace(/_/g,'/'); const data=JSON.parse(atob(part)); const id=Number(data.sub); return Number.isSafeInteger(id)&&id>0 ? {id,email:String(data.email||'')} : null; } catch { return null; }
}
export function getToken(): string | null {
  if (!session || Date.now() >= session.expiresAt) { clearSession(); return null; }
  return session.token;
}
export async function authenticate(mode: 'login' | 'signup', email: string, password: string): Promise<void> {
  const controller = new AbortController();
  const timer = setTimeout(() => controller.abort(), 15000);
  try {
    const response = await fetch(`/api/auth/${mode}`, { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ email, password }), signal: controller.signal });
    if (!response.ok) {
      const messages: Record<number, string> = { 400: 'Verifică emailul și parola. Parola nouă trebuie să aibă între 8 și 72 de caractere.', 401: 'Email sau parolă incorectă.', 409: 'Există deja un cont cu acest email.', 429: 'Prea multe încercări. Încearcă mai târziu.' };
      throw new Error(messages[response.status] || 'Serverul nu poate procesa autentificarea acum.');
    }
    const data = await response.json();
    if (typeof data.token !== 'string' || !data.token || typeof data.expiresInSeconds !== 'number' || data.expiresInSeconds <= 0) throw new Error('Răspuns invalid de la server.');
    session = { token: data.token, expiresAt: Date.now() + data.expiresInSeconds * 1000 };
    sessionStorage.setItem(storageKey, JSON.stringify(session));
  } catch (error) {
    if (controller.signal.aborted) throw new Error('Serverul nu a răspuns la timp. Încearcă din nou.');
    if (error instanceof TypeError) throw new Error('Nu se poate contacta backendul. Verifică dacă este pornit.');
    throw error;
  } finally { clearTimeout(timer); }
}
