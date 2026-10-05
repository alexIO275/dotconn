import './style.css';
import { setupAnalysis } from './analysis';
import { authenticate, clearSession, getToken } from './auth';
import { renderBasic } from './pages';
import { renderWorkspace, renderInvitations } from './workspace';
import { renderChat, cleanupChat } from './chat';
import { escape } from './ui';
import { renderBilling, cleanupBilling } from './billing';
import { setupDesktop } from './desktop';

const main = document.querySelector<HTMLElement>('main')!;
const landing = main.innerHTML;
function navigate(path: string) { history.pushState(null, '', path); renderPage(); window.scrollTo(0, 0); }
window.addEventListener('session-expired', () => {
  cleanupChat(); cleanupBilling();
  if (['/billing', '/pricing'].includes(location.pathname)) renderPage();
});

function loginReturnTarget() {
  const requested = new URLSearchParams(location.search).get('next');
  if (!requested) return '/projects';
  try {
    const target = new URL(requested, location.origin);
    if (target.origin === location.origin && ['/billing', '/pricing'].includes(target.pathname)) return target.pathname + target.search;
  } catch { /* Invalid return paths use the normal projects page. */ }
  return '/projects';
}

const header = document.querySelector<HTMLElement>('.header')!;
const menuToggle = document.querySelector<HTMLButtonElement>('.menu-toggle')!;
function setMenu(open: boolean) { header.classList.toggle('menu-open', open); menuToggle.setAttribute('aria-expanded', String(open)); menuToggle.setAttribute('aria-label', open ? 'Închide meniul' : 'Deschide meniul'); }
menuToggle.addEventListener('click', () => setMenu(!header.classList.contains('menu-open')));
document.addEventListener('keydown', event => { if (event.key === 'Escape' && header.classList.contains('menu-open')) { setMenu(false); menuToggle.focus(); } });

const pageTitles: [RegExp, string][] = [[/^\/projects$/, 'Proiectele mele'], [/^\/projects\/new$/, 'Proiect nou'], [/^\/projects\/\d+$/, 'Workspace'], [/^\/developers$/, 'Programatori'], [/^\/developers\/\d+$/, 'Profil programator'], [/^\/invitations$/, 'Invitațiile mele'], [/^\/chat(\/\d+)?$/, 'Mesaje'], [/^\/profile$/, 'Profilul meu']];

function renderPage() {
  setMenu(false);
  // Old links and typed URLs use /messages; the chat lives at /chat.
  if (/^\/messages\/?$/.test(location.pathname)) history.replaceState(null, '', '/chat');
  const path = window.location.pathname.replace(/\/$/, '') || '/';
  const isRegister = path === '/register';
  const isAuth = isRegister || path === '/login';
  document.querySelectorAll<HTMLAnchorElement>('nav a').forEach((link) => {
    if (link.pathname === path) link.setAttribute('aria-current', 'page');
    else link.removeAttribute('aria-current');
  });
  const nav = document.querySelector<HTMLElement>('nav')!;
  cleanupChat();
  cleanupBilling();
  nav.innerHTML = getToken()
    ? '<a class="auth login" href="/projects">Proiecte</a><a class="auth login" href="/developers">Programatori</a><a class="auth login" href="/invitations">Invitații</a><a class="auth login" href="/chat">Mesaje</a><a class="auth login" href="/profile">Profil</a><a class="auth login" href="/billing">Abonamente</a><button type="button" id="logout-button" class="auth login">Logout</button>'
    : '<a class="auth login" href="/pricing">Abonamente</a><a class="auth login" href="/login">Login</a><a class="auth register" href="/register">Register</a>';
  nav.querySelector('#logout-button')?.addEventListener('click', () => { clearSession(); navigate('/'); });
  nav.querySelectorAll<HTMLAnchorElement>('a').forEach(link => { if (link.pathname === path || path.startsWith(link.pathname + '/')) link.setAttribute('aria-current', 'page'); });
  main.replaceChildren(); const page = document.createElement('div'); page.className = 'route-page'; main.append(page);
  if (path === '/pricing') { document.title = 'Abonamente — MicroCrew'; void renderBilling(page, navigate); return; }
  if (path !== '/' && !isAuth) {
    if (!getToken()) { const loginHref = path === '/billing' ? `/login?next=${encodeURIComponent(path + location.search)}` : '/login'; page.innerHTML = `<section class="auth-page"><h1>Intră în cont.</h1><p>Autentifică-te pentru a continua.</p><a class="auth register" href="${escape(loginHref)}">Login</a></section>`; return; }
    document.title = `${pageTitles.find(([pattern]) => pattern.test(path))?.[1] || 'Pagina nu există'} — MicroCrew`;
    if (path === '/billing') { document.title = 'Abonamentul meu — MicroCrew'; void renderBilling(page, navigate, true); }
    else if (/^\/projects\/\d+$/.test(path)) void renderWorkspace(page, Number(path.split('/')[2]), navigate);
    else if (path === '/invitations') void renderInvitations(page, navigate);
    else if (path === '/chat' || /^\/chat\/\d+$/.test(path)) void renderChat(page, path === '/chat' ? null : Number(path.split('/')[2]), navigate);
    else void renderBasic(page, path, navigate);
    return;
  }
  document.title = isAuth ? `${isRegister ? 'Register' : 'Login'} — MicroCrew` : 'MicroCrew — Găsește colegul pentru proiectul tău';

  if (!isAuth) {
    page.innerHTML = landing;
    if (getToken()) { const links=document.createElement('div'); links.className='landing-actions'; links.innerHTML='<a class="auth login" href="/projects/new">Creează proiect manual</a><a class="auth login" href="/projects">Proiectele mele</a>'; page.querySelector('.intro')?.append(links); }
    const input = document.querySelector<HTMLTextAreaElement>('#project')!;
    document.querySelectorAll<HTMLButtonElement>('[data-role]').forEach((button) => {
      button.addEventListener('click', () => {
        if (!input.value.trim()) input.value = `Caut un programator ${button.dataset.role} pentru proiectul meu: `;
        input.focus();
        input.setSelectionRange(input.value.length, input.value.length);
      });
    });
    page.querySelector('[data-focus-project]')?.addEventListener('click', () => { input.scrollIntoView({ behavior: 'smooth', block: 'center' }); input.focus({ preventScroll: true }); });
    setupAnalysis(page);
    return;
  }

  const loginTarget = loginReturnTarget();
  const authReturnSuffix = loginTarget !== '/projects' ? `?next=${encodeURIComponent(loginTarget)}` : '';
  page.innerHTML = `
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
      <p class="switch-auth">${isRegister ? `Ai deja un cont? <a href="/login${authReturnSuffix}">Login</a>` : `Nu ai încă un cont? <a href="/register${authReturnSuffix}">Register</a>`}</p>
    </section>`;

  if (!isRegister) {
    const host = document.createElement('section'); host.className='demo-accounts'; page.querySelector('.auth-page')?.append(host);
    fetch('/api/demo').then(response => response.ok ? response.json() : null).then(data => {
      if (!page.isConnected || !data || !Array.isArray(data.accounts)) return;
      host.innerHTML='<h2>Testează cu un cont demo</h2><p>Client și programatori cu proiecte și conversații pregătite.</p><div class="demo-options">'+data.accounts.map((account: {email:string;name:string;role:string}) => `<button type="button" class="demo-option" data-email="${escape(account.email)}"><strong>${escape(account.name)}</strong><span>${escape(account.role)}</span></button>`).join('')+'</div>';
      host.querySelectorAll<HTMLButtonElement>('[data-email]').forEach(button=>button.addEventListener('click',()=>{ const email=page.querySelector<HTMLInputElement>('#email')!; email.value=button.dataset.email!; page.querySelector<HTMLInputElement>('#password')!.value='DemoCrew2026!'; page.querySelector<HTMLFormElement>('form')!.requestSubmit(); }));
    }).catch(()=>{});
  }
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
      if (!page.isConnected) return;
      navigate(loginTarget);
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
  if (!link || link.origin !== location.origin || !( ['/', '/login', '/register', '/profile', '/developers', '/projects', '/projects/new', '/invitations', '/chat', '/messages', '/pricing', '/billing'].includes(link.pathname) || /^\/(developers|projects|chat)\/\d+$/.test(link.pathname))) return;
  event.preventDefault();
  history.pushState(null, '', link.pathname + link.search);
  renderPage();
  window.scrollTo(0, 0);
  document.querySelector<HTMLElement>('main h1')?.setAttribute('tabindex', '-1');
  document.querySelector<HTMLElement>('main h1')?.focus();
});
window.addEventListener('popstate', renderPage);
setupDesktop(navigate);
renderPage();
