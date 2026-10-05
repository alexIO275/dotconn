package com.vnhackers.dotconn.billing;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name="billing_events")
public class BillingEvent {
  @Id @Column(length=100) private String id;
  @Column(length=100, nullable=false) private String type;
  @Column(nullable=false) private Instant processedAt=Instant.now();
  protected BillingEvent() {}
  public BillingEvent(String id, String type) { this.id=id; this.type=type; }
}
