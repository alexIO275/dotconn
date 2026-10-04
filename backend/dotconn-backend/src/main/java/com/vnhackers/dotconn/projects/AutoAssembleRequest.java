// Parametri pentru asamblarea automată a echipei din recomandări (ambele opționale).
package com.vnhackers.dotconn.projects;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

public record AutoAssembleRequest(
    @Min(value = 1, message = "Numărul maxim de invitații trebuie să fie între 1 și 20.")
        @Max(value = 20, message = "Numărul maxim de invitații trebuie să fie între 1 și 20.")
        Integer maxInvitations,
    @Min(value = 0, message = "Scorul minim trebuie să fie între 0 și 100.")
        @Max(value = 100, message = "Scorul minim trebuie să fie între 0 și 100.")
        Integer minScore) {}
