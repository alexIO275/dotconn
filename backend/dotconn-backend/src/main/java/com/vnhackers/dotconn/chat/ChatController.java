// Controller REST pentru chat: lista de conversații, istoricul, trimiterea și marcarea ca citit.
package com.vnhackers.dotconn.chat;

import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/chats")
public class ChatController {
  private final ChatService chat;

  public ChatController(ChatService chat) {
    this.chat = chat;
  }

  @GetMapping
  public List<ConversationDto> conversations(@AuthenticationPrincipal Jwt jwt) {
    return chat.conversations(currentUserId(jwt));
  }

  // Cele mai noi mesaje primele; clientul cere pagina următoare pentru istoric mai vechi.
  @GetMapping("/{userId}/messages")
  public Page<ChatMessageDto> messages(
      @AuthenticationPrincipal Jwt jwt,
      @PathVariable Long userId,
      @PageableDefault(size = 50, sort = "id", direction = Sort.Direction.DESC) Pageable pageable) {
    return chat.conversation(currentUserId(jwt), userId, pageable);
  }

  @PostMapping("/{userId}/messages")
  @ResponseStatus(HttpStatus.CREATED)
  public ChatMessageDto send(
      @AuthenticationPrincipal Jwt jwt,
      @PathVariable Long userId,
      @Valid @RequestBody SendMessageRequest req) {
    return chat.send(currentUserId(jwt), userId, req.content());
  }

  @PostMapping("/{userId}/read")
  public Map<String, Integer> markRead(@AuthenticationPrincipal Jwt jwt, @PathVariable Long userId) {
    return Map.of("updated", chat.markRead(currentUserId(jwt), userId));
  }

  private static Long currentUserId(Jwt jwt) {
    try {
      return Long.valueOf(jwt.getSubject());
    } catch (NumberFormatException e) {
      throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Sesiune invalidă.");
    }
  }
}
