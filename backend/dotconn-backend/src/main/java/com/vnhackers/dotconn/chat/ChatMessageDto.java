// DTO pentru un mesaj, trimis atât prin REST cât și prin WebSocket.
package com.vnhackers.dotconn.chat;

import java.time.Instant;

public record ChatMessageDto(
    Long id, Long senderId, Long recipientId, String content, Instant createdAt, Instant readAt) {
  public static ChatMessageDto from(ChatMessage message) {
    return new ChatMessageDto(
        message.getId(),
        message.getSender().getId(),
        message.getRecipient().getId(),
        message.getContent(),
        message.getCreatedAt(),
        message.getReadAt());
  }
}
