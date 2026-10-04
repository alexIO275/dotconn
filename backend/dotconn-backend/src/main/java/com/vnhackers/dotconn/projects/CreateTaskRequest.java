// DTO validat pentru crearea unei sarcini în workspace (asignarea e opțională, dar doar unui membru).
package com.vnhackers.dotconn.projects;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record CreateTaskRequest(
    @NotBlank(message = "Titlul sarcinii este obligatoriu.")
        @Size(max = 150, message = "Titlul poate avea cel mult 150 de caractere.")
        String title,
    @Size(max = 2000, message = "Descrierea poate avea cel mult 2000 de caractere.")
        String description,
    @Positive(message = "Id-ul asignatului este invalid.") Long assigneeId) {}
