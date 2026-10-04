// DTO validat pentru actualizarea parțială (PATCH) a unui proiect; doar proprietarul poate modifica.
package com.vnhackers.dotconn.projects;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

public record UpdateProjectRequest(
    @Size(max = 150, message = "Titlul poate avea cel mult 150 de caractere.") String title,
    @Size(min = 20, max = 5000, message = "Descrierea trebuie să aibă între 20 și 5000 de caractere.")
        String description,
    @Size(max = 1000, message = "Rezumatul poate avea cel mult 1000 de caractere.") String summary,
    @Size(max = 500, message = "Linkul repository-ului poate avea cel mult 500 de caractere.")
        @Pattern(
            regexp = "^https://\\S+$",
            message = "Linkul repository-ului trebuie să fie o adresă https validă.")
        String repositoryUrl,
    @DecimalMin(value = "0.0", message = "Bugetul nu poate fi negativ.")
        @Digits(
            integer = 8,
            fraction = 2,
            message = "Bugetul trebuie să aibă cel mult 8 cifre întregi și 2 zecimale.")
        BigDecimal maxHourlyRate) {}
