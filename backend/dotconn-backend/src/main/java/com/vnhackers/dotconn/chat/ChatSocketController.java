// Controller STOMP: trimiterea de mesaje direct prin WebSocket; erorile ajung doar la expeditor.
package com.vnhackers.dotconn.chat;

import java.security.Principal;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.handler.annotation.MessageExceptionHandler;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.annotation.SendToUser;
import org.springframework.stereotype.Controller;
import org.springframework.web.server.ResponseStatusException;

@Controller
public class ChatSocketController {
  private static final Logger log = LoggerFactory.getLogger(ChatSocketController.class);
  private final ChatService chat;

  public ChatSocketController(ChatService chat) {
    this.chat = chat;
  }

  @MessageMapping("/chat.send")
  public void send(@Payload SocketSendMessage message, Principal principal) {
    chat.send(ChatService.userId(principal), message.recipientId(), message.content());
  }

  @MessageExceptionHandler
  @SendToUser(destinations = "/queue/errors", broadcast = false)
  public Map<String, String> handleError(Exception e) {
    if (e instanceof ResponseStatusException rse && rse.getReason() != null) {
      return Map.of("message", rse.getReason());
    }
    log.warn("Chat WebSocket error", e);
    return Map.of("message", "Mesajul nu a putut fi trimis.");
  }
}
