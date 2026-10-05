package com.vnhackers.dotconn.billing;

import com.vnhackers.dotconn.user.User;
import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name="billing_accounts")
public class BillingAccount {
  @Id @Column(name="user_id") private Long userId;
  @MapsId @OneToOne(fetch=FetchType.LAZY, optional=false) @JoinColumn(name="user_id") private User user;
  @Column(length=100, unique=true) private String stripeCustomerId;
  @Column(length=100, unique=true) private String stripeSubscriptionId;
  @Column(length=20, nullable=false) private String plan="free";
  @Column(length=30, nullable=false) private String status="free";
  private Instant periodEnd;
  @Column(nullable=false) private boolean cancelAtPeriodEnd;
  @Column(length=100, unique=true) private String checkoutSessionId;
  @Column(length=20) private String checkoutPlan;
  private Instant checkoutExpiresAt;
  @Column(nullable=false) private long checkoutSequence;
  @Column(length=7, nullable=false) private String usageMonth="";
  @Column(nullable=false) private int analysesUsed;
  @Column(nullable=false) private Instant updatedAt=Instant.now();
  protected BillingAccount() {}
  public BillingAccount(User user) { this.user=user; }
  @PreUpdate void updated() { updatedAt=Instant.now(); }
  public Long getUserId() { return userId; }
  public User getUser() { return user; }
  public String getStripeCustomerId() { return stripeCustomerId; }
  public void setStripeCustomerId(String value) { stripeCustomerId=value; }
  public String getStripeSubscriptionId() { return stripeSubscriptionId; }
  public void setStripeSubscriptionId(String value) { stripeSubscriptionId=value; }
  public String getPlan() { return plan; }
  public void setPlan(String value) { plan=value; }
  public String getStatus() { return status; }
  public void setStatus(String value) { status=value; }
  public Instant getPeriodEnd() { return periodEnd; }
  public void setPeriodEnd(Instant value) { periodEnd=value; }
  public boolean isCancelAtPeriodEnd() { return cancelAtPeriodEnd; }
  public void setCancelAtPeriodEnd(boolean value) { cancelAtPeriodEnd=value; }
  public String getCheckoutSessionId() { return checkoutSessionId; }
  public void setCheckoutSessionId(String value) { checkoutSessionId=value; }
  public String getCheckoutPlan() { return checkoutPlan; }
  public void setCheckoutPlan(String value) { checkoutPlan=value; }
  public Instant getCheckoutExpiresAt() { return checkoutExpiresAt; }
  public void setCheckoutExpiresAt(Instant value) { checkoutExpiresAt=value; }
  public long getCheckoutSequence() { return checkoutSequence; }
  public void setCheckoutSequence(long value) { checkoutSequence=value; }
  public String getUsageMonth() { return usageMonth; }
  public void setUsageMonth(String value) { usageMonth=value; }
  public int getAnalysesUsed() { return analysesUsed; }
  public void setAnalysesUsed(int value) { analysesUsed=value; }
}
