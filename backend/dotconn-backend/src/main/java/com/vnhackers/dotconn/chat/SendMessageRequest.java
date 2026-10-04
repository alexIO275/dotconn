// Corpul cererii REST pentru trimiterea unui mesaj (destinatarul vine din URL).
package com.vnhackers.dotconn.chat;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record SendMessageRequest(
    @NotBlank @Size(max = ChatService.MAX_CONTENT_LENGTH) String content) {}
