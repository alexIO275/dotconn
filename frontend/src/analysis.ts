import { getToken, clearSession } from './auth';
interface Analysis { summary: string; roles: string[]; tasks: string[]; existingStack: string[]; missingInformation: string[] }
const roleNames: Record<string, string> = { frontend: 'Frontend', backend: 'Backend', 'full-stack': 'Full-stack', mobile: 'Mobile', devops: 'DevOps', qa: 'QA / testare', data: 'Date', security: 'Securitate' };
function isAnalysis(value: unknown): value is Analysis {
  if (!value || typeof value !== 'object') return false;
  const item = value as Record<string, unknown>;
  return typeof item.summary === 'string' && ['roles', 'tasks', 'existingStack', 'missingInformation'].every(key => Array.isArray(item[key]) && (item[key] as unknown[]).every(v => typeof v === 'string'));
}
export function setupAnalysis() {
  const form = document.querySelector<HTMLFormElement>('#analysis-form')!;
  const input = document.querySelector<HTMLTextAreaElement>('#project')!;
  const button = form.querySelector<HTMLButtonElement>('button[type="submit"]')!;
  const status = document.querySelector<HTMLElement>('#analysis-status')!;
  const result = document.querySelector<HTMLElement>('#analysis-result')!;
  let pending = false;
  form.addEventListener('submit', async event => {
    event.preventDefault();
    if (pending) return;
    input.setCustomValidity(input.value.trim().length < 20 ? 'Descrie proiectul în cel puțin 20 de caractere.' : '');
    if (!form.reportValidity()) return;
    const token = getToken();
    if (!token) { status.textContent = 'Intră în cont sau creează un cont pentru a analiza proiectul.'; return; }
    const controller = new AbortController();
    pending = true; button.disabled = true; button.textContent = 'Se analizează…';
    status.textContent = ''; result.hidden = true;
    const timer = setTimeout(() => controller.abort(), 40000);
    try {
      const response = await fetch('/api/project-analysis', { method: 'POST', headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${token}` }, credentials: 'same-origin', body: JSON.stringify({ description: input.value.trim() }), signal: controller.signal });
      if (!response.ok) {
        if (response.status === 401) clearSession();
        let message = response.status === 401 || response.status === 403 ? 'Sesiunea a expirat sau accesul a fost refuzat. Intră din nou în cont.' : 'Analiza nu este disponibilă. Verifică dacă backendul este pornit și Groq este configurat.';
        try { const error = await response.json(); if (typeof error.message === 'string') message = error.message; } catch { /* The proxy may return plain text. */ }
        throw new Error(message);
      }
      const data: unknown = await response.json();
      if (!isAnalysis(data)) throw new Error('Serverul a returnat un rezultat invalid.');
      result.replaceChildren();
      const heading = document.createElement('h2'); heading.textContent = 'Pentru proiectul tău'; result.append(heading);
      const summary = document.createElement('p'); summary.textContent = data.summary; result.append(summary);
      for (const [title, values] of [['Roluri necesare', data.roles.map(role => roleNames[role] || role)], ['Sarcini', data.tasks], ['Tehnologii existente', data.existingStack], ['De clarificat', data.missingInformation]] as const) {
        if (!values.length) continue;
        const label = document.createElement('h3'); label.textContent = title;
        const list = document.createElement('ul');
        values.forEach(value => { const li = document.createElement('li'); li.textContent = value; list.append(li); });
        result.append(label, list);
      }
      result.hidden = false; status.textContent = 'Verifică analiza înainte să cauți un coleg.';
    } catch (error) {
      status.textContent = controller.signal.aborted ? 'Analiza a durat prea mult. Încearcă din nou.' : error instanceof Error ? error.message : 'Analiza nu a putut fi finalizată.';
    } finally { clearTimeout(timer); pending = false; button.disabled = false; button.textContent = 'Analizează proiectul'; }
  });
  input.addEventListener('input', () => input.setCustomValidity(''));
}
