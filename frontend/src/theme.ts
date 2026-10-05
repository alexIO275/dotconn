const KEY = 'microcrew-theme';

export type Theme = 'dark' | 'light';

export function currentTheme(): Theme {
  try {
    const stored = localStorage.getItem(KEY);
    if (stored === 'dark' || stored === 'light') return stored;
  } catch { /* localStorage indisponibil: folosim tema sistemului. */ }
  return window.matchMedia('(prefers-color-scheme: dark)').matches ? 'dark' : 'light';
}

export function applyTheme(theme: Theme) {
  document.documentElement.dataset.theme = theme;
  try { localStorage.setItem(KEY, theme); } catch { /* persistenta optionala */ }
  document.querySelector('meta[name="theme-color"]')?.setAttribute('content', theme === 'dark' ? '#0f1626' : '#ffffff');
  const button = document.querySelector<HTMLButtonElement>('#theme-toggle');
  if (button) {
    button.setAttribute('aria-pressed', String(theme === 'dark'));
    button.setAttribute('aria-label', theme === 'dark' ? 'Comută la tema luminoasă' : 'Comută la tema întunecată');
    button.textContent = theme === 'dark' ? 'light' : 'dark';
  }
}

export function toggleTheme() {
  applyTheme(currentTheme() === 'dark' ? 'light' : 'dark');
}

export function initTheme() {
  applyTheme(currentTheme());
  document.querySelector('#theme-toggle')?.addEventListener('click', toggleTheme);
  // Daca utilizatorul n-a ales explicit o tema, urmarim tema sistemului.
  try {
    if (localStorage.getItem(KEY)) return;
  } catch { return; }
  window.matchMedia('(prefers-color-scheme: dark)').addEventListener('change', event => {
    try {
      if (localStorage.getItem(KEY)) return;
    } catch { return; }
    applyTheme(event.matches ? 'dark' : 'light');
  });
}
