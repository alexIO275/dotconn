// Payload-ul STOMP trimis la /app/chat.send; expeditorul nu e aici, se ia din sesiunea autentificată.
package com.vnhackers.dotconn.chat;

public record SocketSendMessage(Long recipientId, String content) {}
