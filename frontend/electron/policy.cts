const loopbackHosts = new Set(['127.0.0.1', 'localhost', '[::1]']);
const paymentHosts = new Set(['checkout.stripe.com', 'billing.stripe.com']);

export function serviceOrigin(value: string): string {
  const url = new URL(value);
  if (url.username || url.password || url.search || url.hash || url.pathname !== '/' ||
      !(url.protocol === 'https:' || (url.protocol === 'http:' && loopbackHosts.has(url.hostname)))) {
    throw new Error('Serverul trebuie să aibă o origine HTTPS sau HTTP pe localhost, fără cale sau credențiale.');
  }
  return url.origin;
}

export function stripeUrl(value: unknown): string {
  if (typeof value !== 'string' || value.length > 8192) throw new Error('Adresă Stripe invalidă.');
  const url = new URL(value);
  if (url.protocol !== 'https:' || !paymentHosts.has(url.hostname) || url.username || url.password || (url.port && url.port !== '443')) {
    throw new Error('Poți deschide doar Checkout sau Customer Portal Stripe.');
  }
  return url.href;
}

export function externalUrl(value: unknown): string {
  if (typeof value !== 'string' || value.length > 4096) throw new Error('Link extern invalid.');
  const url = new URL(value);
  if (url.protocol !== 'https:' || !url.hostname || url.username || url.password || (url.port && url.port !== '443')) {
    throw new Error('Linkurile externe trebuie să folosească HTTPS, fără credențiale.');
  }
  return url.href;
}

export function billingReturnPath(value: string, origins: ReadonlySet<string>): string | null {
  let url: URL;
  try { url = new URL(value); } catch { return null; }
  if (!origins.has(url.origin) || url.username || url.password || url.pathname !== '/billing') return null;
  const checkout = url.searchParams.get('checkout');
  if (checkout === 'cancelled') return '/billing?checkout=cancelled';
  const id = url.searchParams.get('session_id');
  if (checkout === 'success' && id && /^cs_test_[A-Za-z0-9]{8,240}$/.test(id)) {
    return `/billing?checkout=success&session_id=${encodeURIComponent(id)}`;
  }
  return '/billing';
}

export function contentSecurityPolicy(origin: string): string {
  return `default-src 'self'; script-src 'self'; style-src 'self' 'unsafe-inline'; img-src 'self' data:; font-src 'self'; connect-src 'self' ${origin.replace(/^http/, 'ws')}; object-src 'none'; base-uri 'self'; frame-src 'none'; frame-ancestors 'none'; form-action 'self'`;
}
