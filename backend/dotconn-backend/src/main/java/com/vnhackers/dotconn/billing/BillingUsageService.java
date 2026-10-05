package com.vnhackers.dotconn.billing;

import java.util.function.Supplier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class BillingUsageService {
  private final BillingStore store;
  public BillingUsageService(BillingStore store) { this.store=store; }

  /** One user row is held across the provider call. Failed/invalid results roll back without usage. */
  @Transactional(timeout=45)
  public <T> T successfulAnalysis(Long userId, Supplier<T> analysis) {
    BillingAccount account=store.lock(userId);
    int limit=store.entitlement(account).limit();
    if (account.getAnalysesUsed()>=limit) throw new AnalysisLimitException(limit);
    T result=analysis.get();
    account.setAnalysesUsed(account.getAnalysesUsed()+1);
    return result;
  }
}
