package com.vnhackers.dotconn.chat;

import com.vnhackers.dotconn.projects.Project;
import com.vnhackers.dotconn.user.User;
import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "project_chat_messages", indexes = @Index(name="idx_project_chat_history", columnList="project_id,id"))
public class ProjectChatMessage {
  @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
  @ManyToOne(fetch=FetchType.LAZY, optional=false) @JoinColumn(name="project_id") private Project project;
  @ManyToOne(fetch=FetchType.LAZY, optional=false) @JoinColumn(name="sender_id") private User sender;
  @Column(length=2000, nullable=false) private String content;
  @Column(nullable=false, updatable=false) private Instant createdAt=Instant.now();
  protected ProjectChatMessage() {}
  public ProjectChatMessage(Project project, User sender, String content) { this.project=project; this.sender=sender; this.content=content; }
  public Long getId() { return id; }
  public Project getProject() { return project; }
  public User getSender() { return sender; }
  public String getContent() { return content; }
  public Instant getCreatedAt() { return createdAt; }
}
