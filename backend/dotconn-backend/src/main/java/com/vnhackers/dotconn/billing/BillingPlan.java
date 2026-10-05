package com.vnhackers.dotconn.billing;

import java.util.List;

public enum BillingPlan {
  FREE("free", "Free", 0, 3),
  BRONZE("bronze", "Bronze", 999, 25),
  SILVER("silver", "Silver", 1999, 50),
  GOLD("gold", "Gold", 3499, 200);

  private final String id, displayName;
  private final int price, limit;
  BillingPlan(String id, String displayName, int price, int limit) {
    this.id=id; this.displayName=displayName; this.price=price; this.limit=limit;
  }
  public String id() { return id; }
  public int price() { return price; }
  public int limit() { return limit; }
  public String displayName() { return displayName; }
  public List<String> features() {
    return List.of(limit + " analize AI reușite / lună calendaristică UTC",
        "Profiluri, echipe, invitații, workspace și chat",
        this == FREE ? "Acces gratuit, fără card" : "Abonament lunar Stripe TEST; fără bani reali");
  }
  public static BillingPlan fromId(String id) {
    for (var plan : values()) if (plan.id.equals(id)) return plan;
    throw new IllegalArgumentException("Plan invalid.");
  }
}
