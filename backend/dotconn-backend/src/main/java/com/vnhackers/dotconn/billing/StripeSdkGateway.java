package com.vnhackers.dotconn.billing;

import com.stripe.exception.SignatureVerificationException;
import com.stripe.exception.StripeException;
import com.stripe.net.RequestOptions;
import com.stripe.net.Webhook;
import com.stripe.param.CustomerCreateParams;
import com.stripe.param.SubscriptionListParams;
import com.stripe.param.checkout.SessionCreateParams;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.json.JsonMapper;

@Component
public class StripeSdkGateway implements StripeGateway {
  private final BillingConfiguration configuration;
  private final JsonMapper mapper=JsonMapper.builder().build();
  public StripeSdkGateway(BillingConfiguration configuration) { this.configuration=configuration; }

  private RequestOptions options(String idempotencyKey) {
    var builder=RequestOptions.builder().setApiKey(configuration.secretKey())
        .setConnectTimeout(3000).setReadTimeout(10000).setMaxNetworkRetries(1);
    if (idempotencyKey!=null) builder.setIdempotencyKey(idempotencyKey);
    return builder.build();
  }
  @FunctionalInterface private interface StripeCall<T> { T run() throws StripeException; }
  private <T> T request(StripeCall<T> call) {
    try { return call.run(); }
    catch (StripeException e) {
      // Do not expose the upstream error, request object, API key, or customer details.
      throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
          "Stripe TEST nu a putut procesa cererea. Verifică setările sau încearcă din nou.");
    }
  }

  @Override public StripeGateway.Price price(String priceId) {
    return request(() -> priceSnapshot(com.stripe.model.Price.retrieve(priceId, options(null))));
  }
  @Override public StripeGateway.Customer createCustomer(Long userId, String email, String idempotencyKey) {
    return request(() -> {
      var customer=com.stripe.model.Customer.create(CustomerCreateParams.builder().setEmail(email)
          .putMetadata("userId", userId.toString()).putMetadata("app", "microcrew").build(), options(idempotencyKey));
      return new StripeGateway.Customer(customer.getId(), Boolean.TRUE.equals(customer.getLivemode()), metadata(customer.getMetadata(), "userId"));
    });
  }
  SessionCreateParams checkoutParams(Long userId, String customerId, BillingPlan plan, String priceId) {
    return SessionCreateParams.builder().setMode(SessionCreateParams.Mode.SUBSCRIPTION)
        .setCustomer(customerId).setClientReferenceId(userId.toString())
        .setSuccessUrl(configuration.frontendUrl()+"/billing?checkout=success&session_id={CHECKOUT_SESSION_ID}")
        .setCancelUrl(configuration.frontendUrl()+"/billing?checkout=cancelled")
        .setAllowPromotionCodes(false).addAllowedPaymentMethodType(SessionCreateParams.AllowedPaymentMethodType.CARD)
        .addLineItem(SessionCreateParams.LineItem.builder().setPrice(priceId).setQuantity(1L).build())
        .putMetadata("app", "microcrew").putMetadata("userId", userId.toString()).putMetadata("plan", plan.id())
        .setSubscriptionData(SessionCreateParams.SubscriptionData.builder()
            .putMetadata("app", "microcrew").putMetadata("userId", userId.toString()).putMetadata("plan", plan.id()).build()).build();
  }
  @Override public Checkout createCheckout(Long userId, String customerId, BillingPlan plan, String priceId, String idempotencyKey) {
    return request(() -> checkoutSnapshot(com.stripe.model.checkout.Session.create(
        checkoutParams(userId, customerId, plan, priceId), options(idempotencyKey))));
  }
  @Override public Checkout checkout(String sessionId) {
    return request(() -> checkoutSnapshot(com.stripe.model.checkout.Session.retrieve(sessionId, options(null))));
  }
  @Override public Checkout expireCheckout(String sessionId, String idempotencyKey) {
    return request(() -> {
      var session=new com.stripe.model.checkout.Session();
      session.setId(sessionId);
      return checkoutSnapshot(session.expire(options(idempotencyKey)));
    });
  }
  @Override public Subscription subscription(String subscriptionId) {
    return request(() -> subscriptionSnapshot(com.stripe.model.Subscription.retrieve(subscriptionId, options(null))));
  }
  @Override public List<Subscription> subscriptions(String customerId) {
    return request(() -> {
      var result=new ArrayList<Subscription>();
      var params=SubscriptionListParams.builder().setCustomer(customerId)
          .setStatus(SubscriptionListParams.Status.ALL).setLimit(100L).build();
      for (var subscription : com.stripe.model.Subscription.list(params, options(null)).autoPagingIterable()) {
        result.add(subscriptionSnapshot(subscription));
      }
      return List.copyOf(result);
    });
  }
  @Override public String portal(String customerId) {
    return request(() -> com.stripe.model.billingportal.Session.create(
        com.stripe.param.billingportal.SessionCreateParams.builder().setCustomer(customerId)
            .setReturnUrl(configuration.frontendUrl()+"/billing").build(), options(null)).getUrl());
  }

  @Override public StripeGateway.Event verifyWebhook(String body, String signature) {
    try {
      var verified=Webhook.constructEvent(body, signature, configuration.webhookSecret());
      var object=mapper.readTree(body).path("data").path("object");
      String customer=object.path("customer").isString() ? object.path("customer").asString() : object.path("customer").path("id").asString("");
      String type=verified.getType();
      String subscription=type.startsWith("customer.subscription.") ? object.path("id").asString("") : object.path("subscription").asString("");
      if (subscription.isEmpty()) subscription=object.path("parent").path("subscription_details").path("subscription").asString("");
      String session=type.startsWith("checkout.session.") ? object.path("id").asString("") : "";
      return new StripeGateway.Event(verified.getId(), type, Boolean.TRUE.equals(verified.getLivemode()), customer, subscription, session);
    } catch (SignatureVerificationException | RuntimeException e) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Semnătură Stripe sau eveniment invalid.");
    }
  }

  private static String metadata(Map<String,String> metadata, String key) { return metadata==null ? "" : metadata.getOrDefault(key, ""); }
  private static StripeGateway.Price priceSnapshot(com.stripe.model.Price price) {
    var recurring=price.getRecurring();
    return new StripeGateway.Price(price.getId(), Boolean.TRUE.equals(price.getLivemode()), Boolean.TRUE.equals(price.getActive()),
        price.getCurrency(), price.getUnitAmount(), recurring==null ? null : recurring.getInterval(), recurring==null ? null : recurring.getIntervalCount());
  }
  private static Checkout checkoutSnapshot(com.stripe.model.checkout.Session session) {
    return new Checkout(session.getId(), Boolean.TRUE.equals(session.getLivemode()), session.getCustomer(), session.getClientReferenceId(),
        metadata(session.getMetadata(), "userId"), metadata(session.getMetadata(), "plan"), session.getMode(), session.getStatus(),
        session.getPaymentStatus(), session.getSubscription(), session.getUrl(), session.getExpiresAt()==null ? null : Instant.ofEpochSecond(session.getExpiresAt()));
  }
  static Subscription subscriptionSnapshot(com.stripe.model.Subscription subscription) {
    List<Item> items=new ArrayList<>();
    Instant periodEnd=null;
    if (subscription.getItems()!=null && subscription.getItems().getData()!=null) {
      for (var item : subscription.getItems().getData()) {
        if (item.getPrice()!=null) items.add(new Item(priceSnapshot(item.getPrice()), item.getQuantity()));
        if (item.getCurrentPeriodEnd()!=null) {
          var itemEnd=Instant.ofEpochSecond(item.getCurrentPeriodEnd());
          if (periodEnd==null || itemEnd.isBefore(periodEnd)) periodEnd=itemEnd;
        }
      }
    }
    // Flexible billing can schedule cancellation using cancel_at while
    // cancel_at_period_end stays false. canceled_at is only the request time.
    Instant cancelAt=subscription.getCancelAt()==null ? null : Instant.ofEpochSecond(subscription.getCancelAt());
    if (cancelAt!=null && (periodEnd==null || cancelAt.isBefore(periodEnd))) periodEnd=cancelAt;
    boolean cancellationScheduled=!"canceled".equals(subscription.getStatus())
        && (Boolean.TRUE.equals(subscription.getCancelAtPeriodEnd()) || cancelAt!=null);
    return new Subscription(subscription.getId(), Boolean.TRUE.equals(subscription.getLivemode()), subscription.getCustomer(),
        metadata(subscription.getMetadata(), "userId"), metadata(subscription.getMetadata(), "plan"), subscription.getStatus(),
        cancellationScheduled, periodEnd, List.copyOf(items));
  }
}
