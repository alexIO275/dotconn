package com.vnhackers.dotconn.billing;

import java.time.Instant;
import org.springframework.stereotype.Component;

@Component
public class BillingClock {
  public Instant now() { return Instant.now(); }
}
