declare global {
  interface Window {
    microcrewDesktop?: {
      readonly platform: string;
      openStripe(url: string): Promise<void>;
      openExternal(url: string): Promise<void>;
      onBillingReturn(callback: (path: string) => void): () => void;
    };
  }
}

export function setupDesktop(navigate: (path: string) => void) {
  const desktop = window.microcrewDesktop;
  if (!desktop) return;
  desktop.onBillingReturn(path => {
    const target = new URL(path, location.origin);
    if (target.origin === location.origin && target.pathname === '/billing') navigate(target.pathname + target.search);
  });
  document.addEventListener('click', event => {
    if (!(event instanceof MouseEvent) || event.button !== 0 || !(event.target instanceof Element)) return;
    const link = event.target.closest<HTMLAnchorElement>('a[href]');
    if (!link || link.origin === location.origin || link.protocol !== 'https:') return;
    event.preventDefault();
    void desktop.openExternal(link.href).catch(() => { window.alert('Nu am putut deschide linkul în browser.'); });
  });
}

/** Desktop keeps the authenticated window open while Stripe uses an isolated window. */
export async function openStripeSession(url: string): Promise<boolean> {
  if (window.microcrewDesktop) { await window.microcrewDesktop.openStripe(url); return true; }
  location.assign(url);
  return false;
}
