package com.vnhackers.dotconn.billing;

import com.vnhackers.dotconn.user.User;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import jakarta.persistence.PersistenceContext;
import java.time.YearMonth;
import java.time.ZoneOffset;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Component
public class BillingStore {
  private final BillingAccountRepository accounts;
  private final BillingClock clock;
  @PersistenceContext private EntityManager entityManager;
  public BillingStore(BillingAccountRepository accounts, BillingClock clock) { this.accounts=accounts; this.clock=clock; }

  @Transactional(propagation=Propagation.MANDATORY)
  public BillingAccount lock(Long userId) {
    // The user row also serializes the first account creation, before a billing row exists.
    User user=entityManager.find(User.class, userId, LockModeType.PESSIMISTIC_WRITE);
    if (user==null) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Contul nu a fost găsit.");
    BillingAccount account=accounts.findById(userId).orElse(null);
    if (account==null) account=accounts.saveAndFlush(new BillingAccount(user));
    else entityManager.refresh(account, LockModeType.PESSIMISTIC_WRITE);
    String month=YearMonth.from(clock.now().atOffset(ZoneOffset.UTC)).toString();
    if (!month.equals(account.getUsageMonth())) { account.setUsageMonth(month); account.setAnalysesUsed(0); }
    return account;
  }
  public BillingPlan entitlement(BillingAccount account) {
    if (!"active".equals(account.getStatus()) && !"trialing".equals(account.getStatus())) return BillingPlan.FREE;
    if (account.getPeriodEnd()==null || !account.getPeriodEnd().isAfter(clock.now())) return BillingPlan.FREE;
    try { return BillingPlan.fromId(account.getPlan()); } catch (IllegalArgumentException ignored) { return BillingPlan.FREE; }
  }
}
