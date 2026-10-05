// Repository pentru invitații, cu căutări pe proiect și pe invitat (pentru verificări de acces).
package com.vnhackers.dotconn.projects;

import java.util.List;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InvitationRepository extends JpaRepository<Invitation, Long> {
  List<Invitation> findByProjectId(Long projectId);

  List<Invitation> findByProjectIdAndStatus(Long projectId, InvitationStatus status);
  Page<Invitation> findByInviteeId(Long inviteeId, Pageable pageable);

  Page<Invitation> findByInviteeIdAndStatus(Long inviteeId, InvitationStatus status, Pageable pageable);

  Optional<Invitation> findByIdAndInviteeId(Long id, Long inviteeId);

  Optional<Invitation> findByProjectIdAndInviteeId(Long projectId, Long inviteeId);

  boolean existsByProjectIdAndInviteeIdAndStatus(
      Long projectId, Long inviteeId, InvitationStatus status);

  long countByProjectIdAndStatus(Long projectId, InvitationStatus status);
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select i from Invitation i where i.id = :id and i.invitee.id = :userId")
  Optional<Invitation> findForResponse(@Param("id") Long id, @Param("userId") Long userId);

  @Query("select i.project.id from Invitation i where i.id = :id and i.invitee.id = :userId")
  Optional<Long> findProjectIdForInvitee(@Param("id") Long id, @Param("userId") Long userId);
}
