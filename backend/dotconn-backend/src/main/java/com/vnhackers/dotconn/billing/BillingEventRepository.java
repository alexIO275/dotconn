package com.vnhackers.dotconn.billing;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface BillingEventRepository extends JpaRepository<BillingEvent,String> {
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select e from BillingEvent e where e.id=:id")
  Optional<BillingEvent> findForUpdate(@Param("id") String id);
}
