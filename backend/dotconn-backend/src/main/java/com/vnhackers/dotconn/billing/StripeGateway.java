package com.vnhackers.dotconn.billing;

import java.time.Instant;
import java.util.List;

/** Narrow boundary around Stripe; the application never trusts browser-supplied price or status. */
public interface StripeGateway {
  record Price(String id, boolean liveMode, boolean active, String currency, Long amount,
      String interval, Long intervalCount) {}
  record Customer(String id, boolean liveMode, String userId) {}
  record Checkout(String id, boolean liveMode, String customerId, String clientReferenceId,
      String userId, String plan, String mode, String status, String paymentStatus,
      String subscriptionId, String url, Instant expiresAt) {}
  record Subscription(String id, boolean liveMode, String customerId, String userId, String plan,
      String status, boolean cancelAtPeriodEnd, Instant periodEnd, List<Item> items) {}
  record Item(Price price, Long quantity) {}
  record Event(String id, String type, boolean liveMode, String customerId,
      String subscriptionId, String sessionId) {}

  Price price(String priceId);
  Customer createCustomer(Long userId, String email, String idempotencyKey);
  Checkout createCheckout(Long userId, String customerId, BillingPlan plan, String priceId, String idempotencyKey);
  Checkout checkout(String sessionId);
  Checkout expireCheckout(String sessionId, String idempotencyKey);
  Subscription subscription(String subscriptionId);
  List<Subscription> subscriptions(String customerId);
  String portal(String customerId);
  Event verifyWebhook(String rawBody, String signature);
}
