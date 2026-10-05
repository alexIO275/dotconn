import { api, ApiError } from './api';
import { getToken } from './auth';
import { escape } from './ui';
import { openStripeSession } from './desktop';
import './billing.css';

type PlanId = 'free' | 'bronze' | 'silver' | 'gold';
type Mode = 'stripe-test' | 'unconfigured';
type Navigate = (path: string) => void;
interface Plan { id: PlanId; name: string; monthlyPriceCents: number; currency: string; monthlyAnalysisLimit: number; features: string[] }
interface Catalogue { mode: Mode; plans: Plan[] }
interface Subscription { plan: PlanId; status: string; mode: Mode; monthlyAnalysisLimit: number; analysesUsed: number; remainingAnalyses: number; usageResetAt: string; periodEnd: string | null; cancelAtPeriodEnd: boolean; canManage: boolean }
interface HostedSession { url: string; mode: Mode }
const lifecycles = new Set<() => void>();
const paidPlans: PlanId[] = ['bronze', 'silver', 'gold'];
const terminalStatuses = new Set(['free', 'canceled', 'incomplete_expired']);
const statusNames: Record<string, string> = {
  free: 'Plan gratuit', active: 'Activ', trialing: 'Perioadă de probă', incomplete: 'Confirmare în curs',
  incomplete_expired: 'Checkout expirat', past_due: 'Plata de test necesită atenție', canceled: 'Anulat',
  unpaid: 'Plată de test neconfirmată', paused: 'Suspendat',
};

export function cleanupBilling() { [...lifecycles].forEach(dispose => dispose()); }
function lifecycle(root: HTMLElement) {
  let active = true;
  const timers = new Map<number, (alive: boolean) => void>();
  const cleanups: (() => void)[] = [];
  const dispose = () => {
    if (!active) return;
    active = false;
    timers.forEach((resolve, timer) => { clearTimeout(timer); resolve(false); });
    timers.clear(); cleanups.forEach(cleanup => cleanup()); lifecycles.delete(dispose);
  };
  const observer = new MutationObserver(() => { if (!root.isConnected) dispose(); });
  observer.observe(document.body, { childList: true, subtree: true });
  cleanups.push(() => observer.disconnect());
  lifecycles.add(dispose);
  return {
    alive: () => active && root.isConnected,
    add: (cleanup: () => void) => cleanups.push(cleanup),
    wait: (milliseconds: number) => new Promise<boolean>(resolve => {
      if (!active || !root.isConnected) { resolve(false); return; }
      const timer = window.setTimeout(() => { timers.delete(timer); resolve(active && root.isConnected); }, milliseconds);
      timers.set(timer, resolve);
    }),
  };
}
function errorMessage(error: unknown) {
  if (error instanceof ApiError && error.status === 503) return 'Plata de test nu este încă disponibilă. Poți folosi în continuare planul gratuit.';
  return error instanceof Error ? error.message : 'Nu am putut încărca abonamentele. Încearcă din nou.';
}
function isCatalogue(value: unknown): value is Catalogue {
  if (!value || typeof value !== 'object') return false;
  const data = value as Catalogue;
  return ['stripe-test', 'unconfigured'].includes(data.mode) && Array.isArray(data.plans) &&
    data.plans.length === 4 && new Set(data.plans.map(plan => plan?.id)).size === 4 && data.plans.every(plan =>
      plan && ['free', ...paidPlans].includes(plan.id) && typeof plan.name === 'string' &&
      Number.isSafeInteger(plan.monthlyPriceCents) && plan.monthlyPriceCents >= 0 && plan.currency === 'usd' &&
      Number.isSafeInteger(plan.monthlyAnalysisLimit) && plan.monthlyAnalysisLimit > 0 &&
      Array.isArray(plan.features) && plan.features.every(feature => typeof feature === 'string'));
}
function isSubscription(value: unknown): value is Subscription {
  if (!value || typeof value !== 'object') return false;
  const data = value as Subscription;
  return ['free', ...paidPlans].includes(data.plan) && typeof data.status === 'string' &&
    ['stripe-test', 'unconfigured'].includes(data.mode) && Number.isSafeInteger(data.monthlyAnalysisLimit) && data.monthlyAnalysisLimit > 0 &&
    Number.isSafeInteger(data.analysesUsed) && data.analysesUsed >= 0 && Number.isSafeInteger(data.remainingAnalyses) && data.remainingAnalyses >= 0 &&
    typeof data.usageResetAt === 'string' && !Number.isNaN(Date.parse(data.usageResetAt)) &&
    (data.periodEnd === null || (typeof data.periodEnd === 'string' && !Number.isNaN(Date.parse(data.periodEnd)))) &&
    typeof data.cancelAtPeriodEnd === 'boolean' && typeof data.canManage === 'boolean';
}
async function getCatalogue(life: ReturnType<typeof lifecycle>): Promise<Catalogue> {
  const controller = new AbortController();
  const timer = setTimeout(() => controller.abort(), 15000);
  life.add(() => controller.abort());
  try {
    const response = await fetch('/api/billing/plans', { signal: controller.signal, credentials: 'same-origin' });
    if (!response.ok) throw new Error('Abonamentele nu sunt disponibile acum. Încearcă din nou.');
    const data: unknown = await response.json();
    if (!isCatalogue(data)) throw new Error('Nu am putut confirma informațiile despre abonamente. Încearcă din nou.');
    return data;
  } catch (error) {
    if (controller.signal.aborted) throw new Error('Serverul nu a răspuns la timp. Încearcă din nou.');
    if (error instanceof TypeError) throw new Error('Nu putem contacta serverul. Încearcă din nou.');
    throw error;
  } finally { clearTimeout(timer); }
}
async function getSubscription(): Promise<Subscription> {
  const data: unknown = await api('/billing/subscription');
  if (!isSubscription(data)) throw new Error('Nu am putut confirma starea abonamentului. Încearcă din nou.');
  return data;
}
function price(plan: Plan) {
  return new Intl.NumberFormat('ro-RO', { style: 'currency', currency: 'USD', currencyDisplay: 'code', minimumFractionDigits: plan.monthlyPriceCents ? 2 : 0 }).format(plan.monthlyPriceCents / 100);
}
function utcDate(value: string) { return new Date(value).toLocaleDateString('ro-RO', { day: 'numeric', month: 'long', year: 'numeric', timeZone: 'UTC' }); }
function planName(plan: PlanId, catalogue: Catalogue) { return plan === 'free' ? 'Gratuit' : catalogue.plans.find(item => item.id === plan)?.name || plan; }
function hostedUrl(session: HostedSession, portal: boolean) {
  let url: URL;
  try { url = new URL(session.url); } catch { throw new Error('Nu am putut deschide pagina de plată de test. Încearcă din nou.'); }
  const allowed = portal ? ['billing.stripe.com'] : ['checkout.stripe.com'];
  if (session.mode !== 'stripe-test' || url.protocol !== 'https:' || !allowed.includes(url.hostname) || url.username || url.password || (url.port && url.port !== '443')) {
    throw new Error('Nu am putut confirma pagina de plată de test. Nu ai fost redirecționat.');
  }
  return url.href;
}

/** Prices and entitlements always come from the backend; URL parameters never activate a plan. */
export async function renderBilling(root: HTMLElement, navigate: Navigate, accountPage = false) {
  const life = lifecycle(root);
  const returned = new URLSearchParams(location.search);
  const checkoutReturn = accountPage ? returned.get('checkout') : null;
  const sessionId = accountPage ? returned.get('session_id') : null;
  let catalogue: Catalogue;
  let subscription: Subscription | null = null;
  let subscriptionError = '';
  let busy = false;
  root.innerHTML = '<section class="billing-page"><h1>Abonamente</h1><p class="billing-muted" role="status">Se încarcă planurile…</p></section>';
  try {
    catalogue = await getCatalogue(life);
    if (!life.alive()) return;
  } catch (error) {
    if (!life.alive()) return;
    root.innerHTML = `<section class="billing-page"><h1>Abonamente</h1><p class="billing-message error" role="alert">${escape(errorMessage(error))}</p><button class="billing-button" type="button" data-billing-retry>Încearcă din nou</button></section>`;
    root.querySelector<HTMLButtonElement>('[data-billing-retry]')!.onclick = () => { cleanupBilling(); void renderBilling(root, navigate, accountPage); };
    return;
  }
  if (getToken()) {
    try { subscription = await getSubscription(); }
    catch (error) { subscriptionError = errorMessage(error); }
    if (!life.alive()) return;
  }
  const authenticated = Boolean(getToken());
  const configured = catalogue.mode === 'stripe-test';
  const free = catalogue.plans.find(plan => plan.id === 'free')!;
  const plans = paidPlans.map(id => catalogue.plans.find(plan => plan.id === id)!);
  root.innerHTML = `<section class="billing-page" aria-labelledby="billing-title"><header class="billing-heading"><div><h1 id="billing-title">${accountPage ? 'Abonamentul meu' : 'Abonamente MicroCrew'}</h1><p>Alege câte idei analizezi cu AI. Proiectele, echipele și chatul sunt disponibile și gratuit.</p></div>${accountPage ? '<a href="/projects" class="billing-back">Înapoi la proiecte</a>' : authenticated ? '<a href="/billing" class="billing-back">Vezi abonamentul meu</a>' : ''}</header><div class="billing-mode ${configured ? 'test' : 'unconfigured'}" role="status">${configured ? '<strong>Testare: nu se debitează bani reali</strong><span>Checkoutul și gestionarea abonamentului se deschid în Stripe.</span>' : '<strong>Plata de test nu este încă disponibilă</strong><span>Planul gratuit și colaborarea în echipă rămân disponibile.</span>'}</div><div data-subscription></div><p class="billing-message" data-billing-feedback role="status" aria-live="polite"></p><div class="billing-plans" aria-label="Compară abonamentele">${plans.map(plan => `<article class="billing-plan" data-plan-card="${plan.id}"><header><h2>${escape(plan.name)}</h2><span class="billing-current-tag" data-current-label="${plan.id}" hidden>Planul tău</span></header><p class="billing-price">${escape(price(plan))}<span>/ lună</span></p><p class="billing-analysis-count"><strong>${plan.monthlyAnalysisLimit}</strong> analize AI pe lună</p><ul>${plan.features.map(feature => `<li>${escape(feature)}</li>`).join('')}</ul><button type="button" class="billing-button" data-select-plan="${plan.id}" data-plan-name="${escape(plan.name)}"></button><p class="billing-card-feedback" data-plan-feedback="${plan.id}" role="status"></p></article>`).join('')}</div><div class="billing-free"><div><h2>Începe gratuit</h2><p><strong>${free.monthlyAnalysisLimit} analize AI pe lună</strong>, cu profil, proiecte, echipe și chat. ${escape(price(free))}, fără abonament.</p></div><div data-free-action></div></div><p class="billing-details">Cotele AI se resetează la începutul fiecărei luni calendaristice, în UTC. Abonamentele sunt lunare, fără perioadă de probă. Prețurile sunt propuse pentru testarea modelului de abonament.</p><section class="billing-common"><h2>Colaborarea este inclusă în toate planurile</h2><div><p><strong>Găsește echipa</strong><span>Profiluri, recomandări și invitații, cu rolurile potrivite proiectului.</span></p><p><strong>Lucrează împreună</strong><span>Sarcini comune, repository și contract API în workspace.</span></p><p><strong>Ține legătura</strong><span>Chat privat și de proiect, cu mesaje în timp real.</span></p></div></section></section>`;
  const feedback = root.querySelector<HTMLElement>('[data-billing-feedback]')!;
  const notice = (content: string, variant: 'normal' | 'error' | 'success' = 'normal') => {
    if (!life.alive()) return;
    feedback.textContent = content; feedback.className = `billing-message ${variant}`;
  };
  const renderAccount = () => {
    if (!life.alive()) return;
    const panel = root.querySelector<HTMLElement>('[data-subscription]')!;
    if (!subscription) {
      panel.innerHTML = subscriptionError ? `<div class="billing-account-error"><p class="billing-message error">${escape(subscriptionError)}</p>${getToken() ? '<button type="button" class="billing-button" data-refresh-subscription>Actualizează starea contului</button>' : '<a class="billing-button" href="/login?next=%2Fbilling">Login pentru a vedea abonamentul</a>'}</div>` : '';
    } else {
      const used = subscription.analysesUsed;
      const limit = subscription.monthlyAnalysisLimit;
      const percent = Math.min(100, used / limit * 100);
      panel.innerHTML = `<section class="billing-account" aria-label="Abonamentul și utilizarea contului"><div class="billing-account-plan"><span>Planul curent</span><h2>${escape(planName(subscription.plan, catalogue))}</h2><p>${escape(statusNames[subscription.status] || 'Stare în verificare')}${subscription.cancelAtPeriodEnd ? ' · Anulare programată' : ''}</p>${subscription.periodEnd ? `<small>${subscription.cancelAtPeriodEnd ? 'Accesul inclus în abonament se încheie' : 'Perioada curentă se încheie'} pe ${escape(utcDate(subscription.periodEnd))} (UTC).</small>` : ''}${subscription.canManage ? '<button type="button" class="billing-button" data-billing-portal>Gestionează în Stripe</button>' : ''}</div><div class="billing-usage"><div class="billing-usage-heading"><h3>Analize AI</h3><strong>${used} <span>/ ${limit}</span></strong></div><div class="billing-progress" role="progressbar" aria-label="Analize AI utilizate în această lună" aria-valuemin="0" aria-valuemax="${limit}" aria-valuenow="${Math.min(used, limit)}" aria-valuetext="${used} analize folosite din ${limit}"><span style="width:${percent}%"></span></div><p>${subscription.remainingAnalyses ? `${subscription.remainingAnalyses} analize disponibile.` : 'Cota lunară este consumată. Poți continua proiectele, echipele și chatul.'}</p><small>Se resetează pe ${escape(utcDate(subscription.usageResetAt))} (UTC).</small><button type="button" class="billing-refresh" data-refresh-subscription>Actualizează starea contului</button></div></section>`;
    }
    panel.querySelector<HTMLButtonElement>('[data-billing-portal]')?.addEventListener('click', () => void openPortal());
    panel.querySelector<HTMLButtonElement>('[data-refresh-subscription]')?.addEventListener('click', () => void refreshAccount());
    const requiresPortal = Boolean(subscription?.canManage && !terminalStatuses.has(subscription.status));
    root.querySelectorAll<HTMLButtonElement>('[data-select-plan]').forEach(button => {
      const plan = button.dataset.selectPlan!;
      const current = subscription?.plan === plan;
      button.textContent = current ? 'Planul tău' : !configured ? 'Plata de test indisponibilă' : requiresPortal ? 'Gestionează abonamentul' : getToken() ? `Alege ${button.dataset.planName}` : 'Login pentru a alege';
      button.disabled = busy || current || !configured || (Boolean(getToken()) && !subscription);
      button.classList.toggle('primary', !current && !requiresPortal);
      root.querySelector<HTMLElement>(`[data-plan-card="${plan}"]`)!.classList.toggle('current', current);
      root.querySelector<HTMLElement>(`[data-current-label="${plan}"]`)!.hidden = !current;
    });
    root.querySelector<HTMLElement>('[data-free-action]')!.innerHTML = !getToken() ? '<a class="billing-button" href="/register?next=%2Fbilling">Creează un cont gratuit</a>' : subscription?.plan === 'free' ? '<span class="billing-free-current">Planul tău actual</span>' : '<span class="billing-free-current">Disponibil fără abonament</span>';
    root.querySelectorAll<HTMLButtonElement>('[data-billing-portal], [data-refresh-subscription]').forEach(button => button.disabled = busy);
  };
  const refreshAccount = async () => {
    if (busy || !life.alive()) return;
    busy = true; renderAccount(); notice('Se verifică starea abonamentului…');
    try {
      const updated = await getSubscription();
      if (!life.alive()) return;
      subscription = updated; subscriptionError = ''; notice('Starea contului a fost actualizată.');
    } catch (error) { if (life.alive()) notice(errorMessage(error), 'error'); }
    finally { busy = false; renderAccount(); }
  };
  const openPortal = async () => {
    if (busy || !life.alive() || !subscription?.canManage) return;
    busy = true; renderAccount(); notice('Se deschide gestionarea abonamentului de test…');
    try {
      const session = await api<HostedSession>('/billing/portal', 'POST');
      if (!life.alive()) return;
      if (await openStripeSession(hostedUrl(session, true)) && life.alive()) {
        busy = false; renderAccount(); notice('Gestionează abonamentul în fereastra Stripe.');
      }
    } catch (error) { if (life.alive()) notice(errorMessage(error), 'error'); busy = false; renderAccount(); }
  };
  root.querySelectorAll<HTMLButtonElement>('[data-select-plan]').forEach(button => button.onclick = async () => {
    if (busy || !life.alive()) return;
    if (!getToken()) { navigate('/login?next=%2Fbilling'); return; }
    if (!subscription || !configured) return;
    if (subscription.canManage && !terminalStatuses.has(subscription.status)) { await openPortal(); return; }
    const plan = button.dataset.selectPlan as PlanId;
    if (!paidPlans.includes(plan) || subscription.plan === plan) return;
    busy = true; renderAccount(); notice('Se pregătește checkoutul de test…');
    try {
      const session = await api<HostedSession>('/billing/checkout', 'POST', { plan });
      if (!life.alive()) return;
      if (await openStripeSession(hostedUrl(session, false)) && life.alive()) {
        busy = false; renderAccount(); notice('Finalizează plata de test în fereastra Stripe. Contul se actualizează la revenire.');
      }
    } catch (error) { if (life.alive()) notice(errorMessage(error), 'error'); busy = false; renderAccount(); }
  });
  renderAccount();
  if (checkoutReturn === 'cancelled') {
    notice('Checkoutul a fost închis. Planul afișat este cel confirmat pentru contul tău.');
    history.replaceState(history.state, '', location.pathname);
  } else if (checkoutReturn === 'success') {
    if (!sessionId || !/^cs_test_[A-Za-z0-9]{8,240}$/.test(sessionId)) {
      notice('Nu putem verifica acest checkout. Planul afișat provine din contul tău.', 'error');
      history.replaceState(history.state, '', location.pathname);
      return;
    }
    busy = true; renderAccount(); notice('Verificăm checkoutul de test cu Stripe…');
    try {
      // Retry only verification, never checkout creation. Activation requires authoritative backend data.
      for (let attempt = 0; attempt < 3; attempt++) {
        const confirmed: unknown = await api('/billing/sync', 'POST', { sessionId });
        if (!life.alive()) return;
        if (!isSubscription(confirmed)) throw new Error('Starea abonamentului nu a putut fi confirmată.');
        subscription = confirmed; subscriptionError = ''; renderAccount();
        if (subscription.mode === 'stripe-test' && subscription.plan !== 'free' && subscription.status === 'active') {
          notice(`Abonamentul de test ${planName(subscription.plan, catalogue)} este activ. Nu s-au debitat bani reali.`, 'success');
          break;
        }
        if (attempt === 2) notice('Checkoutul a fost verificat. Abonamentul nu este încă activ; poți actualiza starea contului peste câteva momente.');
        else if (!(await life.wait(1800))) return;
      }
      if (life.alive()) history.replaceState(history.state, '', location.pathname);
    } catch (error) { if (life.alive()) notice(`Checkoutul nu a putut fi confirmat. ${errorMessage(error)}`, 'error'); }
    finally { busy = false; renderAccount(); }
  }
}
