// DTO validat pentru actualizarea parțială (PATCH) a unei sarcini din workspace.
package com.vnhackers.dotconn.projects;

import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record UpdateTaskRequest(
    @Size(max = 150, message = "Titlul poate avea cel mult 150 de caractere.") String title,
    @Size(max = 2000, message = "Descrierea poate avea cel mult 2000 de caractere.")
        String description,
    TaskStatus status,
    @Positive(message = "Id-ul asignatului este invalid.") Long assigneeId) {}
