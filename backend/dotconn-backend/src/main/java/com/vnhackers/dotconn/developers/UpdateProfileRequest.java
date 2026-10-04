package com.vnhackers.dotconn.developers;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.List;

public record UpdateProfileRequest(
    @Size(max = 100, message = "Numele poate avea cel mult 100 de caractere.") String displayName,
    @Size(max = 2000, message = "Descrierea poate avea cel mult 2000 de caractere.")
        String description,
    DeveloperRole role,
    @Size(max = 20, message = "Poți adăuga cel mult 20 de tehnologii.")
        List<@Size(max = 50, message = "Fiecare tehnologie poate avea cel mult 50 de caractere.") String>
        technologies,
    @Min(value = 0, message = "Experiența nu poate fi negativă.")
        @Max(value = 60, message = "Experiența nu poate depăși 60 de ani.")
        Integer experienceYears,
    @Size(max = 255, message = "Linkul GitHub poate avea cel mult 255 de caractere.")
        @Pattern(
            regexp = "^https://github\\.com/[^\\s/]+(/[^\\s]*)?$",
            message = "Linkul GitHub trebuie să înceapă cu https://github.com/.")
        String githubUrl,
    Availability availability,
    @DecimalMin(value = "0.0", message = "Tariful nu poate fi negativ.")
        @Digits(
            integer = 8,
            fraction = 2,
            message = "Tariful trebuie să aibă cel mult 8 cifre întregi și 2 zecimale.")
        BigDecimal hourlyRate) {}
