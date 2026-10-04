import './style.css';

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
    return;
  }

  main.innerHTML = `
    <section class="auth-page" aria-labelledby="auth-title">
      <a class="back-link" href="/">Înapoi la pagina principală</a>
      <h1 id="auth-title">${isRegister ? 'Hai să construim.' : 'Bine ai revenit.'}</h1>
      <p class="auth-description">${isRegister ? 'Creează un cont și găsește colegul pentru următorul proiect.' : 'Intră în cont și continuă proiectele tale.'}</p>
      <form id="auth-form">
        ${isRegister ? '<div class="field"><label for="name">Nume</label><input id="name" name="name" type="text" autocomplete="name" placeholder="Numele tău" required maxlength="100"></div>' : ''}
        <div class="field"><label for="email">Email</label><input id="email" name="email" type="email" autocomplete="email" placeholder="nume@exemplu.ro" required></div>
        <div class="field"><label for="password">Parolă</label><div class="password-field"><input id="password" name="password" type="password" autocomplete="${isRegister ? 'new-password' : 'current-password'}" ${isRegister ? 'minlength="8" aria-describedby="password-help"' : ''} required><button type="button" class="password-toggle" aria-controls="password" aria-pressed="false">Arată</button></div>${isRegister ? '<small id="password-help">Minimum 8 caractere.</small>' : ''}</div>
        ${isRegister ? '<div class="field"><label for="confirm-password">Confirmă parola</label><input id="confirm-password" name="confirmPassword" type="password" autocomplete="new-password" required minlength="8"></div>' : ''}
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
    const name = document.querySelector<HTMLInputElement>('#name');
    name?.setCustomValidity(name.value.trim() ? '' : 'Introdu numele tău.');
    confirm?.setCustomValidity(confirm.value && confirm.value !== password.value ? 'Parolele nu coincid.' : '');
  }
  form.addEventListener('input', () => { validateFields(); status.textContent = ''; });
  form.addEventListener('submit', (event) => {
    event.preventDefault();
    validateFields();
    if (!form.reportValidity()) return;
    // Integration point: call the authentication backend here.
    // Until connected, no credentials are sent or stored.
    status.textContent = 'Formularul este valid. Autentificarea va fi disponibilă după conectarea la server.';
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
