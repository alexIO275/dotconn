package com.vnhackers.dotconn.billing;

import java.net.URI;
import java.time.Instant;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class BillingService {
  private static final Set<String> TERMINAL=Set.of("free", "canceled", "incomplete_expired");
  private static final Set<String> STATUSES=Set.of("active", "trialing", "past_due", "unpaid", "paused", "incomplete", "incomplete_expired", "canceled");
  private static final Set<String> EVENTS=Set.of("customer.subscription.created", "customer.subscription.updated", "customer.subscription.deleted",
      "checkout.session.completed", "checkout.session.async_payment_succeeded", "invoice.paid", "invoice.payment_failed");
  private final BillingConfiguration configuration;
  private final StripeGateway stripe;
  private final BillingStore store;
  private final BillingAccountRepository accounts;
  private final BillingEventRepository events;
  private final BillingClock clock;
  public BillingService(BillingConfiguration configuration, StripeGateway stripe, BillingStore store,
      BillingAccountRepository accounts, BillingEventRepository events, BillingClock clock) {
    this.configuration=configuration; this.stripe=stripe; this.store=store;
    this.accounts=accounts; this.events=events; this.clock=clock;
  }
  public record PlanDto(String id, String name, int monthlyPriceCents, String currency, int monthlyAnalysisLimit, List<String> features) {}
  public record PlansDto(String mode, List<PlanDto> plans) {}
  public record UrlDto(String url, String mode) {}
  public PlansDto plans() {
    return new PlansDto(configuration.mode(), Arrays.stream(BillingPlan.values())
        .map(p -> new PlanDto(p.id(), p.displayName(), p.price(), "usd", p.limit(), p.features())).toList());
  }

  @Transactional(noRollbackFor=ResponseStatusException.class)
  public BillingSubscriptionDto current(Long userId) {
    var account=store.lock(userId);
    if (configuration.configured() && account.getStripeSubscriptionId()!=null) {
      applySubscription(account, stripe.subscription(account.getStripeSubscriptionId()));
    }
    return dto(account);
  }

  @Transactional(noRollbackFor=ResponseStatusException.class)
  public UrlDto checkout(Long userId, String requestedPlan) {
    requireConfigured();
    BillingPlan plan=paidPlan(requestedPlan);
    validatePrice(stripe.price(configuration.priceId(plan)), plan, true);
    BillingAccount account=store.lock(userId);
    if (account.getStripeSubscriptionId()!=null) {
      applySubscription(account, stripe.subscription(account.getStripeSubscriptionId()));
      if (!TERMINAL.contains(account.getStatus())) throw conflict("Ai deja un abonament. Gestionează-l în portalul Stripe TEST.");
    }
    if (account.getStripeCustomerId()==null) {
      var customer=stripe.createCustomer(userId, account.getUser().getEmail(), "microcrew-customer-"+userId+"-v1");
      if (customer.liveMode() || !userId.toString().equals(customer.userId()) || customer.id()==null || !customer.id().startsWith("cus_")) {
        throw badGateway("Stripe a returnat un client TEST invalid.");
      }
      account.setStripeCustomerId(customer.id());
    }
    // A checkout may have completed before either the webhook or browser return reached us.
    // Inspect the authoritative customer subscriptions before issuing another checkout.
    rejectActiveCustomerSubscriptions(account);
    if (account.getCheckoutSessionId()!=null) {
      var pending=stripe.checkout(account.getCheckoutSessionId());
      validateCheckoutOwnership(account, pending);
      if ("complete".equals(pending.status())) {
        refreshCompletedCheckout(account, pending);
        if (!TERMINAL.contains(account.getStatus())) throw conflict("Plata a fost deja procesată. Actualizează abonamentul.");
      }
      if ("open".equals(pending.status())) {
        if (plan.id().equals(pending.plan()) && pending.expiresAt()!=null && pending.expiresAt().isAfter(clock.now())) {
          return new UrlDto(trustedUrl(pending.url(), "checkout.stripe.com"), "stripe-test");
        }
        expirePendingCheckout(account, pending);
        // Expiration prevents payment on the old session. Also re-check authoritative
        // subscriptions in case a payment completed while we tried to expire it.
        rejectActiveCustomerSubscriptions(account);
      } else if (!Set.of("complete", "expired").contains(pending.status())) {
        throw badGateway("Stripe a returnat o stare de checkout necunoscută.");
      }
    }
    long sequence=account.getCheckoutSequence()+1;
    // Deterministic sequence key and stable params allow retries after an upstream timeout,
    // even when Stripe created the session but the transaction did not store its response.
    var created=stripe.createCheckout(userId, account.getStripeCustomerId(), plan, configuration.priceId(plan),
        "microcrew-checkout-"+userId+"-"+sequence+"-"+plan.id());
    if (created.liveMode() || !account.getStripeCustomerId().equals(created.customerId())
        || !userId.toString().equals(created.userId()) || !userId.toString().equals(created.clientReferenceId())
        || !plan.id().equals(created.plan()) || !"subscription".equals(created.mode()) || !"open".equals(created.status())
        || created.id()==null || !created.id().startsWith("cs_test_") || created.expiresAt()==null) {
      throw badGateway("Stripe a returnat un checkout TEST invalid.");
    }
    String url=trustedUrl(created.url(), "checkout.stripe.com");
    account.setCheckoutSequence(sequence); account.setCheckoutSessionId(created.id());
    account.setCheckoutPlan(plan.id()); account.setCheckoutExpiresAt(created.expiresAt());
    return new UrlDto(url, "stripe-test");
  }

  private void rejectActiveCustomerSubscriptions(BillingAccount account) {
    for (var subscription : stripe.subscriptions(account.getStripeCustomerId())) {
      if (!TERMINAL.contains(subscription.status())) {
        applySubscription(account, subscription);
        throw conflict("Există deja un abonament pentru acest cont. Folosește portalul Stripe TEST.");
      }
    }
  }

  private void refreshCompletedCheckout(BillingAccount account, StripeGateway.Checkout checkout) {
    if (checkout.subscriptionId()==null) throw conflict("Checkout-ul este finalizat. Actualizează abonamentul înainte de a continua.");
    applySubscription(account, stripe.subscription(checkout.subscriptionId()));
  }

  private void expirePendingCheckout(BillingAccount account, StripeGateway.Checkout pending) {
    StripeGateway.Checkout expired;
    try {
      expired=stripe.expireCheckout(pending.id(), "microcrew-expire-"+pending.id());
    } catch (ResponseStatusException failure) {
      // Stripe rejects expiration if payment won the race. Recover its current state,
      // but never create another checkout after an unsuccessful expiration request.
      var latest=stripe.checkout(pending.id());
      validateCheckoutOwnership(account, latest);
      if ("complete".equals(latest.status())) {
        refreshCompletedCheckout(account, latest);
        throw conflict("Plata a fost procesată între timp. Actualizează abonamentul; nu s-a creat un al doilea checkout.");
      }
      throw failure;
    }
    validateCheckoutOwnership(account, expired);
    if (!pending.id().equals(expired.id())) throw badGateway("Stripe nu a confirmat expirarea checkout-ului curent.");
    if ("complete".equals(expired.status())) {
      refreshCompletedCheckout(account, expired);
      throw conflict("Plata a fost procesată între timp. Actualizează abonamentul; nu s-a creat un al doilea checkout.");
    }
    if (!"expired".equals(expired.status()) || expired.subscriptionId()!=null) {
      throw badGateway("Stripe nu a confirmat expirarea checkout-ului curent. Încearcă din nou.");
    }
    account.setCheckoutExpiresAt(clock.now());
  }

  @Transactional
  public UrlDto portal(Long userId) {
    requireConfigured();
    var account=store.lock(userId);
    if (account.getStripeCustomerId()==null || account.getStripeSubscriptionId()==null) {
      throw conflict("Nu ai încă un abonament Stripe de gestionat.");
    }
    return new UrlDto(trustedUrl(stripe.portal(account.getStripeCustomerId()), "billing.stripe.com"), "stripe-test");
  }

  @Transactional(noRollbackFor=ResponseStatusException.class)
  public BillingSubscriptionDto sync(Long userId, String sessionId) {
    requireConfigured();
    if (sessionId==null || !sessionId.matches("cs_test_[A-Za-z0-9]+")) throw badRequest("Checkout TEST invalid.");
    var account=store.lock(userId);
    var checkout=stripe.checkout(sessionId);
    validateCheckoutOwnership(account, checkout);
    if (!"complete".equals(checkout.status()) || checkout.subscriptionId()==null) {
      throw conflict("Checkout-ul nu este finalizat. Abonamentul nu a fost activat.");
    }
    if (!Set.of("paid", "no_payment_required").contains(checkout.paymentStatus())) {
      throw conflict("Plata TEST este încă în așteptare. Actualizează abonamentul după confirmare.");
    }
    applySubscription(account, stripe.subscription(checkout.subscriptionId()));
    return dto(account);
  }

  @Transactional
  public Map<String,Boolean> webhook(String rawBody, String signature) {
    requireConfigured();
    if (signature==null || signature.isBlank()) throw badRequest("Lipsește semnătura Stripe.");
    var event=stripe.verifyWebhook(rawBody, signature);
    if (event.liveMode()) throw badRequest("Evenimentele Stripe live nu sunt acceptate.");
    if (event.id()==null || !event.id().matches("evt_[A-Za-z0-9]+")) throw badRequest("Eveniment Stripe invalid.");
    if (!EVENTS.contains(event.type()) || event.customerId()==null || event.customerId().isBlank()) return Map.of("received", true);
    var known=accounts.findByStripeCustomerId(event.customerId()).orElse(null);
    if (known==null) return Map.of("received", true); // Another application's customer cannot grant access.
    var account=store.lock(known.getUserId());
    if (events.findForUpdate(event.id()).isPresent()) return Map.of("received", true, "duplicate", true);
    if (event.sessionId()!=null && !event.sessionId().isBlank()) {
      var checkout=stripe.checkout(event.sessionId());
      validateCheckoutOwnership(account, checkout);
      if ("complete".equals(checkout.status()) && checkout.subscriptionId()!=null
          && Set.of("paid", "no_payment_required").contains(checkout.paymentStatus())) {
        applySubscription(account, stripe.subscription(checkout.subscriptionId()));
      }
    } else if (event.subscriptionId()!=null && !event.subscriptionId().isBlank()) {
      // Read the CURRENT object, never the possibly stale status inside the event payload.
      applySubscription(account, stripe.subscription(event.subscriptionId()));
    }
    events.saveAndFlush(new BillingEvent(event.id(), event.type()));
    return Map.of("received", true);
  }

  private void validateCheckoutOwnership(BillingAccount account, StripeGateway.Checkout checkout) {
    boolean boundSession=checkout.id()!=null && checkout.id().equals(account.getCheckoutSessionId());
    boolean currentSubscription=checkout.subscriptionId()!=null && checkout.subscriptionId().equals(account.getStripeSubscriptionId());
    if (checkout.liveMode() || account.getStripeCustomerId()==null || !account.getStripeCustomerId().equals(checkout.customerId())
        || !account.getUserId().toString().equals(checkout.userId()) || !account.getUserId().toString().equals(checkout.clientReferenceId())
        || !"subscription".equals(checkout.mode()) || (!boundSession && !currentSubscription)) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Checkout-ul nu aparține acestui cont.");
    }
    paidPlan(checkout.plan());
    if (boundSession && account.getCheckoutPlan()!=null && !account.getCheckoutPlan().equals(checkout.plan())) {
      throw conflict("Planul checkout-ului nu corespunde cererii salvate.");
    }
  }

  private void applySubscription(BillingAccount account, StripeGateway.Subscription subscription) {
    if (subscription.liveMode() || account.getStripeCustomerId()==null || !account.getStripeCustomerId().equals(subscription.customerId())
        || !account.getUserId().toString().equals(subscription.userId()) || subscription.id()==null || !subscription.id().startsWith("sub_")) {
      throw conflict("Abonamentul Stripe TEST nu aparține acestui cont.");
    }
    paidPlan(subscription.plan()); // Server-set creation intent; actual current price determines the current tier.
    if (subscription.items()==null || subscription.items().size()!=1 || !Long.valueOf(1).equals(subscription.items().getFirst().quantity())) {
      throw conflict("Abonamentul nu corespunde unui plan MicroCrew valid.");
    }
    var actualPrice=subscription.items().getFirst().price();
    BillingPlan plan=Arrays.stream(BillingPlan.values()).filter(p -> p!=BillingPlan.FREE)
        .filter(p -> configuration.priceId(p).equals(actualPrice.id())).findFirst()
        .orElseThrow(() -> conflict("Prețul abonamentului nu este un plan MicroCrew configurat."));
    validatePrice(actualPrice, plan, false);
    if (!STATUSES.contains(subscription.status())) throw badGateway("Status de abonament invalid.");
    if (account.getStripeSubscriptionId()!=null && !account.getStripeSubscriptionId().equals(subscription.id()) && !TERMINAL.contains(account.getStatus())) {
      return; // Late events from a previous subscription must not overwrite a newer active one.
    }
    account.setStripeSubscriptionId(subscription.id()); account.setPlan(plan.id()); account.setStatus(subscription.status());
    account.setPeriodEnd(subscription.periodEnd()); account.setCancelAtPeriodEnd(subscription.cancelAtPeriodEnd());
  }

  private void validatePrice(StripeGateway.Price price, BillingPlan plan, boolean requireActive) {
    if (price==null || !configuration.priceId(plan).equals(price.id()) || price.liveMode() || (requireActive && !price.active())
        || !"usd".equals(price.currency()) || !Long.valueOf(plan.price()).equals(price.amount())
        || !"month".equals(price.interval()) || !Long.valueOf(1).equals(price.intervalCount())) {
      throw conflict("Prețul Stripe TEST configurat nu corespunde planului lunar USD MicroCrew.");
    }
  }
  private BillingSubscriptionDto dto(BillingAccount account) {
    var entitlement=store.entitlement(account);
    Instant reset=YearMonth.from(clock.now().atOffset(ZoneOffset.UTC)).plusMonths(1).atDay(1).atStartOfDay().toInstant(ZoneOffset.UTC);
    return new BillingSubscriptionDto(entitlement.id(), account.getStatus(), configuration.mode(), entitlement.limit(),
        account.getAnalysesUsed(), Math.max(0, entitlement.limit()-account.getAnalysesUsed()), reset,
        account.getPeriodEnd(), account.isCancelAtPeriodEnd(), configuration.configured() && account.getStripeSubscriptionId()!=null);
  }
  private BillingPlan paidPlan(String id) {
    try { var plan=BillingPlan.fromId(id); if (plan==BillingPlan.FREE) throw new IllegalArgumentException(); return plan; }
    catch (IllegalArgumentException e) { throw badRequest("Alege un plan plătit valid: bronze, silver sau gold."); }
  }
  private void requireConfigured() {
    if (!configuration.configured()) throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
        "Stripe TEST nu este configurat complet pe server: cheia test, semnătura webhook și cele trei prețuri sunt necesare.");
  }
  private static String trustedUrl(String url, String host) {
    try {
      var uri=URI.create(url);
      if (!"https".equals(uri.getScheme()) || !host.equals(uri.getHost()) || uri.getRawUserInfo()!=null) throw new IllegalArgumentException();
      return url;
    } catch (IllegalArgumentException | NullPointerException e) { throw badGateway("Stripe a returnat o adresă invalidă."); }
  }
  private static ResponseStatusException badRequest(String message) { return new ResponseStatusException(HttpStatus.BAD_REQUEST, message); }
  private static ResponseStatusException conflict(String message) { return new ResponseStatusException(HttpStatus.CONFLICT, message); }
  private static ResponseStatusException badGateway(String message) { return new ResponseStatusException(HttpStatus.BAD_GATEWAY, message); }
}
