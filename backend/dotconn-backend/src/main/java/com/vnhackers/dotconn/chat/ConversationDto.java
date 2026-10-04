// DTO pentru lista de conversații: cu cine, ultimul mesaj și câte mesaje necitite.
package com.vnhackers.dotconn.chat;

public record ConversationDto(
    Long partnerId, String partnerDisplayName, ChatMessageDto lastMessage, long unreadCount) {}
