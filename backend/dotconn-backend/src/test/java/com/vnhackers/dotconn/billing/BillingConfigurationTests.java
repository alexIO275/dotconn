package com.vnhackers.dotconn.billing;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.Test;

class BillingConfigurationTests {
  private BillingConfiguration config(String key, String hook, String origin, String bronze, String silver, String gold) {
    return new BillingConfiguration(key,hook,origin,bronze,silver,gold);
  }
  @Test void rejectsLiveKeysMissingSigningSecretAndInvalidRedirectOrPriceConfiguration() {
    assertThat(config("sk_live_never","whsec_test","http://127.0.0.1:5173","price_b","price_s","price_g").configured()).isFalse();
    assertThat(config("sk_test_fake","","http://127.0.0.1:5173","price_b","price_s","price_g").configured()).isFalse();
    assertThat(config("sk_test_fake","whsec_test","http://evil.example","price_b","price_s","price_g").configured()).isFalse();
    assertThat(config("sk_test_fake","whsec_test","https://user:pass@example.com","price_b","price_s","price_g").configured()).isFalse();
    assertThat(config("sk_test_fake","whsec_test","http://127.0.0.1:5173","price_b","price_b","price_g").configured()).isFalse();
    assertThat(config("sk_test_fake","whsec_test","http://127.0.0.1:5173","price_b","price_s","price_g").configured()).isTrue();
  }
  @Test void checkoutParamsUseOnlyConfiguredPriceAndServerOwnedCustomerAndRedirects() {
    var config=config("sk_test_fake","whsec_test","http://127.0.0.1:5173","price_b","price_s","price_g");
    var params=new StripeSdkGateway(config).checkoutParams(7L,"cus_owned",BillingPlan.SILVER,"price_s");
    assertThat(params.getLineItems()).hasSize(1);
    assertThat(params.getLineItems().getFirst().getPrice()).isEqualTo("price_s");
    assertThat(params.getLineItems().getFirst().getQuantity()).isEqualTo(1L);
    assertThat(params.getLineItems().getFirst().getPriceData()).isNull();
    assertThat(params.getCustomer()).isEqualTo("cus_owned");
    assertThat(params.getMetadata()).containsEntry("userId","7").containsEntry("plan","silver");
    assertThat(params.getSubscriptionData().getMetadata()).containsEntry("userId","7");
    assertThat(params.getSuccessUrl()).isEqualTo("http://127.0.0.1:5173/billing?checkout=success&session_id={CHECKOUT_SESSION_ID}");
    assertThat(params.getCancelUrl()).isEqualTo("http://127.0.0.1:5173/billing?checkout=cancelled");
  }
}
