// Repository pentru invitații, cu căutări pe proiect și pe invitat (pentru verificări de acces).
package com.vnhackers.dotconn.projects;

import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InvitationRepository extends JpaRepository<Invitation, Long> {
  Page<Invitation> findByInviteeId(Long inviteeId, Pageable pageable);

  Page<Invitation> findByInviteeIdAndStatus(Long inviteeId, InvitationStatus status, Pageable pageable);

  Optional<Invitation> findByIdAndInviteeId(Long id, Long inviteeId);

  Optional<Invitation> findByProjectIdAndInviteeId(Long projectId, Long inviteeId);

  boolean existsByProjectIdAndInviteeIdAndStatus(
      Long projectId, Long inviteeId, InvitationStatus status);

  long countByProjectIdAndStatus(Long projectId, InvitationStatus status);
}
