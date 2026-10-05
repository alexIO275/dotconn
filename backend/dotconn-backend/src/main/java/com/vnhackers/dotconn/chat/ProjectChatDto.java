package com.vnhackers.dotconn.chat;
import java.time.Instant;
public record ProjectChatDto(Long id, Long projectId, Long senderId, String senderDisplayName, String content, Instant createdAt) {}
