// Entitate JPA pentru un mesaj privat între doi utilizatori; readAt rămâne null până când destinatarul îl citește.
package com.vnhackers.dotconn.chat;

import com.vnhackers.dotconn.user.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(
    name = "chat_messages",
    indexes = {
      @Index(name = "idx_chat_sender_recipient", columnList = "sender_id, recipient_id"),
      @Index(name = "idx_chat_recipient_read", columnList = "recipient_id, read_at")
    })
public class ChatMessage {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "sender_id")
  private User sender;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "recipient_id")
  private User recipient;

  @Column(length = ChatService.MAX_CONTENT_LENGTH, nullable = false)
  private String content;

  @Column(nullable = false, updatable = false)
  private Instant createdAt = Instant.now();

  @Column(name = "read_at")
  private Instant readAt;

  protected ChatMessage() {}

  public ChatMessage(User sender, User recipient, String content) {
    this.sender = sender;
    this.recipient = recipient;
    this.content = content;
  }

  public Long getId() {
    return id;
  }

  public User getSender() {
    return sender;
  }

  public User getRecipient() {
    return recipient;
  }

  public String getContent() {
    return content;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public Instant getReadAt() {
    return readAt;
  }
}
