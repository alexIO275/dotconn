// Repository pentru proiectele unui utilizator, cu căutare paginată pe owner.
package com.vnhackers.dotconn.projects;

import java.util.Optional;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProjectRepository extends JpaRepository<Project, Long> {
  Page<Project> findByOwnerId(Long ownerId, Pageable pageable);

  Optional<Project> findByIdAndOwnerId(Long id, Long ownerId);
  @Query("select p from Project p where p.owner.id = :userId or exists (select i.id from Invitation i where i.project = p and i.invitee.id = :userId and i.status = com.vnhackers.dotconn.projects.InvitationStatus.ACCEPTED)")
  Page<Project> findVisibleTo(@Param("userId") Long userId, Pageable pageable);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select p from Project p where p.id = :id")
  Optional<Project> findByIdForUpdate(@Param("id") Long id);
}
