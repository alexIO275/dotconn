// DTO validat pentru crearea unui proiect din analiza confirmată/corectată.
package com.vnhackers.dotconn.projects;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.List;

public record CreateProjectRequest(
    @NotBlank @Size(max = 150) String title,
    @NotBlank @Size(min = 20, max = 5000) String description,
    @Size(max = 1000) String summary,
    @NotNull @Size(min = 1, max = 8) List<
            @Pattern(
                regexp = "frontend|backend|full-stack|mobile|devops|qa|data|security",
                message = "Rol invalid.") String>
        roles,
    @NotNull @Size(max = 20) List<@NotBlank @Size(max = 500) String> tasks,
    @Size(max = 20) List<@Size(max = 100) String> existingStack,
    @Size(max = 20) List<@Size(max = 50) String> requiredTechnologies,
    @DecimalMin(value = "0.0", message = "Bugetul nu poate fi negativ.")
        @Digits(
            integer = 8,
            fraction = 2,
            message = "Bugetul trebuie să aibă cel mult 8 cifre întregi și 2 zecimale.")
        BigDecimal maxHourlyRate) {}
