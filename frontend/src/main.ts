import './style.css';
import { setupAnalysis } from './analysis';
import { authenticate, clearSession, getToken } from './auth';

const main = document.querySelector<HTMLElement>('main')!;
const landing = main.innerHTML;

function renderPage() {
  const path = window.location.pathname.replace(/\/$/, '') || '/';
  const isRegister = path === '/register';
  const isAuth = isRegister || path === '/login';
  document.querySelectorAll<HTMLAnchorElement>('nav a').forEach((link) => {
    if (link.pathname === path) link.setAttribute('aria-current', 'page');
    else link.removeAttribute('aria-current');
  });
  const nav = document.querySelector<HTMLElement>('nav')!;
  nav.querySelector('#logout-button')?.remove();
  nav.querySelectorAll<HTMLAnchorElement>('a').forEach(link => { link.hidden = Boolean(getToken()); });
  if (getToken()) {
    const logout = document.createElement('button'); logout.type = 'button'; logout.id = 'logout-button'; logout.className = 'auth login'; logout.textContent = 'Logout';
    logout.addEventListener('click', () => { clearSession(); history.pushState(null, '', '/'); renderPage(); });
    nav.append(logout);
  }
  document.title = isAuth ? `${isRegister ? 'Register' : 'Login'} — MicroCrew` : 'MicroCrew — Găsește colegul pentru proiectul tău';

  if (!isAuth) {
    main.innerHTML = landing;
    const input = document.querySelector<HTMLTextAreaElement>('#project')!;
    document.querySelectorAll<HTMLButtonElement>('[data-role]').forEach((button) => {
      button.addEventListener('click', () => {
        if (!input.value.trim()) input.value = `Caut un programator ${button.dataset.role} pentru proiectul meu: `;
        input.focus();
        input.setSelectionRange(input.value.length, input.value.length);
      });
    });
    setupAnalysis();
    return;
  }

  main.innerHTML = `
    <section class="auth-page" aria-labelledby="auth-title">
      <a class="back-link" href="/">Înapoi la pagina principală</a>
      <h1 id="auth-title">${isRegister ? 'Hai să construim.' : 'Bine ai revenit.'}</h1>
      <p class="auth-description">${isRegister ? 'Creează un cont și găsește colegul pentru următorul proiect.' : 'Intră în cont și continuă proiectele tale.'}</p>
      <form id="auth-form">
        <div class="field"><label for="email">Email</label><input id="email" name="email" type="email" autocomplete="email" placeholder="nume@exemplu.ro" required></div>
        <div class="field"><label for="password">Parolă</label><div class="password-field"><input id="password" name="password" type="password" autocomplete="${isRegister ? 'new-password' : 'current-password'}" ${isRegister ? 'minlength="8" maxlength="72" aria-describedby="password-help"' : ''} required><button type="button" class="password-toggle" aria-controls="password" aria-pressed="false">Arată</button></div>${isRegister ? '<small id="password-help">Între 8 și 72 de caractere.</small>' : ''}</div>
        ${isRegister ? '<div class="field"><label for="confirm-password">Confirmă parola</label><input id="confirm-password" name="confirmPassword" type="password" autocomplete="new-password" required minlength="8" maxlength="72"></div>' : ''}
        <button type="submit" class="auth register submit-button">${isRegister ? 'Creează cont' : 'Intră în cont'}</button>
        <p id="form-status" class="form-status" role="status" aria-live="polite"></p>
      </form>
      <p class="switch-auth">${isRegister ? 'Ai deja un cont? <a href="/login">Login</a>' : 'Nu ai încă un cont? <a href="/register">Register</a>'}</p>
    </section>`;

  const form = document.querySelector<HTMLFormElement>('#auth-form')!;
  const password = document.querySelector<HTMLInputElement>('#password')!;
  const confirm = document.querySelector<HTMLInputElement>('#confirm-password');
  const status = document.querySelector<HTMLElement>('#form-status')!;
  const toggle = document.querySelector<HTMLButtonElement>('.password-toggle')!;

  toggle.addEventListener('click', () => {
    const show = password.type === 'password';
    password.type = show ? 'text' : 'password';
    toggle.textContent = show ? 'Ascunde' : 'Arată';
    toggle.setAttribute('aria-pressed', String(show));
  });
  function validateFields() {
    confirm?.setCustomValidity(confirm.value && confirm.value !== password.value ? 'Parolele nu coincid.' : '');
  }
  form.addEventListener('input', () => { validateFields(); status.textContent = ''; });
  let pending = false;
  form.addEventListener('submit', async (event) => {
    event.preventDefault();
    if (pending) return;
    validateFields();
    if (!form.reportValidity()) return;
    const submit = form.querySelector<HTMLButtonElement>('button[type="submit"]')!;
    pending = true; submit.disabled = true; status.textContent = 'Se verifică…';
    try {
      const email = document.querySelector<HTMLInputElement>('#email')!.value.trim();
      await authenticate(isRegister ? 'signup' : 'login', email, password.value);
      password.value = ''; if (confirm) confirm.value = '';
      history.pushState(null, '', '/'); renderPage();
      const heading = document.querySelector<HTMLElement>('main h1'); heading?.setAttribute('tabindex', '-1'); heading?.focus();
    } catch (error) { status.textContent = error instanceof Error ? error.message : 'Autentificarea a eșuat.'; }
    finally { pending = false; submit.disabled = false; }
  });
}

// Vite serves these routes through its HTML fallback; production hosting must do the same.
document.addEventListener('click', (event) => {
  if (!(event instanceof MouseEvent) || event.button !== 0 || event.metaKey || event.ctrlKey || event.shiftKey || event.altKey) return;
  const target = event.target;
  if (!(target instanceof Element)) return;
  const link = target.closest<HTMLAnchorElement>('a[href]');
  if (!link || link.origin !== location.origin || !['/', '/login', '/register'].includes(link.pathname)) return;
  event.preventDefault();
  history.pushState(null, '', link.pathname);
  renderPage();
  window.scrollTo(0, 0);
  document.querySelector<HTMLElement>('main h1')?.setAttribute('tabindex', '-1');
  document.querySelector<HTMLElement>('main h1')?.focus();
});
window.addEventListener('popstate', renderPage);
renderPage();
