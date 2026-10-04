// Controller pentru vizualizarea și editarea (PATCH) profilului propriu al utilizatorului logat.
package com.vnhackers.dotconn.developers;

import com.vnhackers.dotconn.user.UserRepository;
import jakarta.validation.Valid;
import java.util.LinkedHashSet;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/me/profile")
public class MeProfileController {

  private final DeveloperProfileRepository profiles;
  private final UserRepository users;

  public MeProfileController(DeveloperProfileRepository profiles, UserRepository users) {
    this.profiles = profiles;
    this.users = users;
  }

  @GetMapping
  public DeveloperDetailDto getMine(@AuthenticationPrincipal Jwt jwt) {
    Long userId = currentUserId(jwt);
    return profiles
        .findById(userId)
        .map(DeveloperDetailDto::from)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Nu ai încă un profil."));
  }

  @PatchMapping
  @Transactional
  public DeveloperDetailDto updateMine(
      @AuthenticationPrincipal Jwt jwt, @Valid @RequestBody UpdateProfileRequest request) {
    Long userId = currentUserId(jwt);
    var user =
        users
            .findById(userId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Contul nu a fost găsit."));

    DeveloperProfile profile =
        profiles.findById(userId).orElseGet(() -> new DeveloperProfile(user));

    if (request.displayName() != null) {
      String name = request.displayName().trim();
      profile.setDisplayName(name.isEmpty() ? null : name);
    }
    if (request.description() != null) {
      String description = request.description().trim();
      profile.setDescription(description.isEmpty() ? null : description);
    }
    if (request.role() != null) {
      profile.setRole(request.role());
    }
    if (request.technologies() != null) {
      var cleaned = new LinkedHashSet<String>();
      for (String tech : request.technologies()) {
        if (tech == null) {
          continue;
        }
        String value = tech.trim();
        if (!value.isEmpty()) {
          cleaned.add(value);
        }
      }
      profile.setTechnologies(cleaned.stream().toList());
    }
    if (request.experienceYears() != null) {
      profile.setExperienceYears(request.experienceYears());
    }
    if (request.githubUrl() != null) {
      String url = request.githubUrl().trim();
      profile.setGithubUrl(url.isEmpty() ? null : url);
    }
    if (request.availability() != null) {
      profile.setAvailability(request.availability());
    }
    if (request.hourlyRate() != null) {
      profile.setHourlyRate(request.hourlyRate());
    }

    return DeveloperDetailDto.from(profiles.save(profile));
  }

  private static Long currentUserId(Jwt jwt) {
    try {
      return Long.valueOf(jwt.getSubject());
    } catch (NumberFormatException e) {
      throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Sesiune invalidă.");
    }
  }
}
