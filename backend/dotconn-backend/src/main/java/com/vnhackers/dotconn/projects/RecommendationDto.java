// O recomandare: profil real + scor + explicația potrivirii pe cele 4 dimensiuni.
package com.vnhackers.dotconn.projects;

import com.vnhackers.dotconn.developers.Availability;
import com.vnhackers.dotconn.developers.DeveloperRole;
import java.math.BigDecimal;
import java.util.List;

public record RecommendationDto(
    Long developerId,
    String displayName,
    DeveloperRole role,
    List<String> technologies,
    Integer experienceYears,
    Availability availability,
    BigDecimal hourlyRate,
    int score,
    List<String> reasons) {}
