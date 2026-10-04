// Entitate JPA pentru invitația unui programator într-un proiect; membr devine doar după acceptare.
package com.vnhackers.dotconn.projects;

import com.vnhackers.dotconn.user.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;

@Entity
@Table(
    name = "project_invitations",
    uniqueConstraints = @UniqueConstraint(columnNames = {"project_id", "invitee_id"}))
public class Invitation {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "project_id")
  private Project project;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "inviter_id")
  private User inviter;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "invitee_id")
  private User invitee;

  @Enumerated(EnumType.STRING)
  @Column(length = 20, nullable = false)
  private InvitationStatus status = InvitationStatus.PENDING;

  @Column(nullable = false, updatable = false)
  private Instant createdAt = Instant.now();

  private Instant respondedAt;

  protected Invitation() {}

  public Invitation(Project project, User inviter, User invitee) {
    this.project = project;
    this.inviter = inviter;
    this.invitee = invitee;
  }

  public Long getId() {
    return id;
  }

  public Project getProject() {
    return project;
  }

  public User getInviter() {
    return inviter;
  }

  public User getInvitee() {
    return invitee;
  }

  public InvitationStatus getStatus() {
    return status;
  }

  public void setStatus(InvitationStatus status) {
    this.status = status;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public Instant getRespondedAt() {
    return respondedAt;
  }

  public void setRespondedAt(Instant respondedAt) {
    this.respondedAt = respondedAt;
  }
}
