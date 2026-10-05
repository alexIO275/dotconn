package com.vnhackers.dotconn.billing;

import java.net.URI;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class BillingConfiguration {
  private final String secretKey, webhookSecret, frontendUrl, bronze, silver, gold;
  public BillingConfiguration(@Value("${app.billing.secret-key:}") String secretKey,
      @Value("${app.billing.webhook-secret:}") String webhookSecret,
      @Value("${app.billing.frontend-url:http://127.0.0.1:5173}") String frontendUrl,
      @Value("${app.billing.price-bronze:}") String bronze,
      @Value("${app.billing.price-silver:}") String silver,
      @Value("${app.billing.price-gold:}") String gold) {
    this.secretKey=secretKey.trim(); this.webhookSecret=webhookSecret.trim();
    this.frontendUrl=frontendUrl.replaceAll("/+$", "");
    this.bronze=bronze.trim(); this.silver=silver.trim(); this.gold=gold.trim();
  }
  public boolean configured() {
    return (secretKey.startsWith("sk_test_") || secretKey.startsWith("rk_test_"))
        && webhookSecret.startsWith("whsec_") && validPriceId(bronze) && validPriceId(silver) && validPriceId(gold)
        && !bronze.equals(silver) && !bronze.equals(gold) && !silver.equals(gold) && validOrigin();
  }
  private static boolean validPriceId(String value) { return value.matches("price_[A-Za-z0-9]+" ); }
  private boolean validOrigin() {
    try {
      URI uri=URI.create(frontendUrl);
      return uri.getHost()!=null && uri.getRawUserInfo()==null && uri.getRawQuery()==null && uri.getRawFragment()==null
          && (uri.getPath()==null || uri.getPath().isEmpty())
          && ("https".equals(uri.getScheme()) || ("http".equals(uri.getScheme()) &&
            ("localhost".equals(uri.getHost()) || "127.0.0.1".equals(uri.getHost()))));
    } catch (IllegalArgumentException ignored) { return false; }
  }
  public String mode() { return configured() ? "stripe-test" : "unconfigured"; }
  String secretKey() { return secretKey; }
  String webhookSecret() { return webhookSecret; }
  public String frontendUrl() { return frontendUrl; }
  public String priceId(BillingPlan plan) {
    return switch (plan) { case BRONZE -> bronze; case SILVER -> silver; case GOLD -> gold; default -> ""; };
  }
}
