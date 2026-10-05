// Repository Spring Data pentru căutarea și paginarea profilurilor de programatori.
package com.vnhackers.dotconn.developers;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface DeveloperProfileRepository
    extends JpaRepository<DeveloperProfile, Long>, JpaSpecificationExecutor<DeveloperProfile> {
  @Query("select distinct p from DeveloperProfile p left join fetch p.technologies")
  List<DeveloperProfile> findAllWithTechnologies();

  @Lock(LockModeType.PESSIMISTIC_READ)
  @Query("select p from DeveloperProfile p where p.id = :id")
  Optional<DeveloperProfile> findByIdForTeam(@Param("id") Long id);
}
