// Repository pentru mesaje: istoricul unei conversații, ultimul mesaj per conversație și necititele.
package com.vnhackers.dotconn.chat;

import java.time.Instant;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ChatMessageRepository extends JpaRepository<ChatMessage, Long> {

  @Query(
      """
      SELECT m FROM ChatMessage m
      WHERE (m.sender.id = :me AND m.recipient.id = :other)
         OR (m.sender.id = :other AND m.recipient.id = :me)
      """)
  Page<ChatMessage> findConversation(
      @Param("me") Long me, @Param("other") Long other, Pageable pageable);

  // Cel mai recent mesaj din fiecare conversație a utilizatorului, cele mai noi primele.
  @Query(
      """
      SELECT m FROM ChatMessage m
      WHERE m.id IN (
        SELECT MAX(m2.id) FROM ChatMessage m2
        WHERE m2.sender.id = :me OR m2.recipient.id = :me
        GROUP BY CASE WHEN m2.sender.id = :me THEN m2.recipient.id ELSE m2.sender.id END)
      ORDER BY m.id DESC
      """)
  List<ChatMessage> findLatestPerConversation(@Param("me") Long me);

  interface UnreadCount {
    Long getSenderId();

    long getCount();
  }

  @Query(
      """
      SELECT m.sender.id AS senderId, COUNT(m) AS count FROM ChatMessage m
      WHERE m.recipient.id = :me AND m.readAt IS NULL
      GROUP BY m.sender.id
      """)
  List<UnreadCount> countUnreadBySender(@Param("me") Long me);

  @Modifying
  @Query(
      """
      UPDATE ChatMessage m SET m.readAt = :now
      WHERE m.recipient.id = :me AND m.sender.id = :other AND m.readAt IS NULL
      """)
  int markRead(@Param("me") Long me, @Param("other") Long other, @Param("now") Instant now);
}
