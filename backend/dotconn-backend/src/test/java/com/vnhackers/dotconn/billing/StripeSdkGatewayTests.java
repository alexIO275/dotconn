package com.vnhackers.dotconn.billing;

import static org.assertj.core.api.Assertions.assertThat;

import com.stripe.net.ApiResource;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class StripeSdkGatewayTests {
  private com.stripe.model.Subscription flexibleSubscription(Long cancelAt, long itemEnd, String status) {
    // Shape returned by Stripe Sandbox's flexible billing after portal cancellation.
    String payload="""
        {"id":"sub_flexible","object":"subscription","livemode":false,"customer":"cus_owned",
         "billing_mode":{"type":"flexible"},"status":"%s",
         "metadata":{"userId":"7","plan":"bronze"},
         "cancel_at_period_end":false,"cancel_at":%s,"canceled_at":1791179641,
         "items":{"object":"list","data":[{"id":"si_monthly","object":"subscription_item",
           "quantity":1,"current_period_start":1791179538,"current_period_end":%d,
           "price":{"id":"price_b","object":"price","active":true,"livemode":false,
             "currency":"usd","unit_amount":999,"recurring":{"interval":"month","interval_count":1}}}]}}
        """.formatted(status,cancelAt==null ? "null" : cancelAt,itemEnd);
    return ApiResource.GSON.fromJson(payload,com.stripe.model.Subscription.class);
  }

  @Test void flexiblePortalCancellationUsesCancelAtAndPreservesAccessUntilThatDate() {
    var snapshot=StripeSdkGateway.subscriptionSnapshot(flexibleSubscription(1793857938L,1793857938L,"active"));
    assertThat(snapshot.status()).isEqualTo("active");
    assertThat(snapshot.cancelAtPeriodEnd()).isTrue();
    assertThat(snapshot.periodEnd()).isEqualTo(Instant.ofEpochSecond(1793857938));
    assertThat(snapshot.items().getFirst().price().amount()).isEqualTo(999L);
  }

  @Test void customCancellationCapsAccessBeforeItemPeriodAndClearingItRemovesScheduledFlag() {
    var snapshot=StripeSdkGateway.subscriptionSnapshot(flexibleSubscription(1793857838L,1793857938L,"active"));
    assertThat(snapshot.periodEnd()).isEqualTo(Instant.ofEpochSecond(1793857838));
    assertThat(snapshot.cancelAtPeriodEnd()).isTrue();
    var resumed=StripeSdkGateway.subscriptionSnapshot(flexibleSubscription(null,1793857938L,"active"));
    assertThat(resumed.cancelAtPeriodEnd()).isFalse();
    assertThat(resumed.periodEnd()).isEqualTo(Instant.ofEpochSecond(1793857938));
    var canceled=StripeSdkGateway.subscriptionSnapshot(flexibleSubscription(1793857838L,1793857938L,"canceled"));
    assertThat(canceled.cancelAtPeriodEnd()).isFalse();
  }
}
