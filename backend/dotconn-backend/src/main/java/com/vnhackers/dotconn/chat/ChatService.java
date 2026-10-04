// Logica de chat: salvează mesajele și le livrează în timp real pe coada privată a fiecărui participant.
package com.vnhackers.dotconn.chat;

import com.vnhackers.dotconn.developers.DeveloperProfile;
import com.vnhackers.dotconn.developers.DeveloperProfileRepository;
import com.vnhackers.dotconn.user.User;
import com.vnhackers.dotconn.user.UserRepository;
import java.security.Principal;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class ChatService {
  public static final int MAX_CONTENT_LENGTH = 2000;
  // Destinațiile la care se abonează clientul: /user/queue/messages și /user/queue/read.
  static final String MESSAGES_QUEUE = "/queue/messages";
  static final String READ_QUEUE = "/queue/read";

  private final ChatMessageRepository messages;
  private final UserRepository users;
  private final DeveloperProfileRepository profiles;
  private final SimpMessagingTemplate messaging;

  public ChatService(
      ChatMessageRepository messages,
      UserRepository users,
      DeveloperProfileRepository profiles,
      SimpMessagingTemplate messaging) {
    this.messages = messages;
    this.users = users;
    this.profiles = profiles;
    this.messaging = messaging;
  }

  // Nu e @Transactional: mesajul se livrează prin WebSocket doar după ce a fost salvat.
  public ChatMessageDto send(Long senderId, Long recipientId, String rawContent) {
    String content = rawContent == null ? "" : rawContent.strip();
    if (recipientId == null) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Destinatarul lipsește.");
    }
    if (content.isEmpty()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Mesajul nu poate fi gol.");
    }
    if (content.length() > MAX_CONTENT_LENGTH) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST,
          "Mesajul depășește " + MAX_CONTENT_LENGTH + " de caractere.");
    }
    if (senderId.equals(recipientId)) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST, "Nu îți poți trimite mesaje singur.");
    }
    User sender =
        users
            .findById(senderId)
            .orElseThrow(
                () -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Sesiune invalidă."));
    User recipient = requireUser(recipientId);

    ChatMessageDto dto = ChatMessageDto.from(messages.save(new ChatMessage(sender, recipient, content)));
    messaging.convertAndSendToUser(recipientId.toString(), MESSAGES_QUEUE, dto);
    // Și expeditorului, ca mesajul să apară în celelalte tab-uri/dispozitive ale lui.
    messaging.convertAndSendToUser(senderId.toString(), MESSAGES_QUEUE, dto);
    return dto;
  }

  @Transactional(readOnly = true)
  public Page<ChatMessageDto> conversation(Long me, Long other, Pageable pageable) {
    requireUser(other);
    return messages.findConversation(me, other, pageable).map(ChatMessageDto::from);
  }

  @Transactional(readOnly = true)
  public List<ConversationDto> conversations(Long me) {
    List<ChatMessage> latest = messages.findLatestPerConversation(me);
    Map<Long, Long> unread = new HashMap<>();
    for (var row : messages.countUnreadBySender(me)) {
      unread.put(row.getSenderId(), row.getCount());
    }
    List<Long> partnerIds = latest.stream().map(m -> partnerOf(m, me)).toList();
    Map<Long, String> names = new HashMap<>();
    for (DeveloperProfile profile : profiles.findAllById(partnerIds)) {
      names.put(profile.getId(), profile.getDisplayName());
    }
    return latest.stream()
        .map(
            m -> {
              Long partnerId = partnerOf(m, me);
              return new ConversationDto(
                  partnerId,
                  names.get(partnerId),
                  ChatMessageDto.from(m),
                  unread.getOrDefault(partnerId, 0L));
            })
        .toList();
  }

  // Marchează ca citite mesajele primite de la `other` și îl anunță pe acesta (confirmare de citire).
  @Transactional
  public int markRead(Long me, Long other) {
    requireUser(other);
    Instant now = Instant.now();
    int updated = messages.markRead(me, other, now);
    if (updated > 0) {
      messaging.convertAndSendToUser(
          other.toString(), READ_QUEUE, Map.of("readerId", me, "readAt", now.toString()));
    }
    return updated;
  }

  static Long userId(Principal principal) {
    if (principal == null) {
      throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Sesiune invalidă.");
    }
    try {
      return Long.valueOf(principal.getName());
    } catch (NumberFormatException e) {
      throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Sesiune invalidă.");
    }
  }

  private User requireUser(Long id) {
    return users
        .findById(id)
        .orElseThrow(
            () -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Utilizatorul nu a fost găsit."));
  }

  private static Long partnerOf(ChatMessage message, Long me) {
    Long senderId = message.getSender().getId();
    return senderId.equals(me) ? message.getRecipient().getId() : senderId;
  }
}
