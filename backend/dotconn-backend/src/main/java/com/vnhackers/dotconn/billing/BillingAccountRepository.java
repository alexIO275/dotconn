package com.vnhackers.dotconn.billing;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BillingAccountRepository extends JpaRepository<BillingAccount,Long> {
  Optional<BillingAccount> findByStripeCustomerId(String stripeCustomerId);
}
