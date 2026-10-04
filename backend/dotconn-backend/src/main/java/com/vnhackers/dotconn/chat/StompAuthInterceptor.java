// Autentifică sesiunile STOMP cu același JWT ca API-ul REST și permite abonări doar la cozile proprii.
package com.vnhackers.dotconn.chat;

import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessageDeliveryException;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;

@Component
public class StompAuthInterceptor implements ChannelInterceptor {
  private final JwtDecoder jwtDecoder;

  public StompAuthInterceptor(JwtDecoder jwtDecoder) {
    this.jwtDecoder = jwtDecoder;
  }

  @Override
  public Message<?> preSend(Message<?> message, MessageChannel channel) {
    StompHeaderAccessor accessor =
        MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
    if (accessor == null || accessor.getCommand() == null) return message;

    StompCommand command = accessor.getCommand();
    if (command == StompCommand.CONNECT) {
      accessor.setUser(authenticate(accessor.getFirstNativeHeader("Authorization")));
    } else if (command == StompCommand.SUBSCRIBE || command == StompCommand.SEND) {
      if (accessor.getUser() == null) {
        throw new MessageDeliveryException("Neautentificat.");
      }
      // /user/queue/... e rezolvat de Spring la coada acestei sesiuni, deci nimeni nu poate asculta mesajele altuia.
      String destination = accessor.getDestination();
      if (command == StompCommand.SUBSCRIBE
          && (destination == null || !destination.startsWith("/user/queue/"))) {
        throw new MessageDeliveryException("Abonare nepermisă: " + destination);
      }
    }
    return message;
  }

  private JwtAuthenticationToken authenticate(String header) {
    if (header == null || !header.startsWith("Bearer ")) {
      throw new MessageDeliveryException("Lipsește header-ul Authorization.");
    }
    try {
      Jwt jwt = jwtDecoder.decode(header.substring("Bearer ".length()));
      Long.valueOf(jwt.getSubject());
      return new JwtAuthenticationToken(jwt);
    } catch (JwtException | NumberFormatException e) {
      throw new MessageDeliveryException("Token invalid sau expirat.");
    }
  }
}
