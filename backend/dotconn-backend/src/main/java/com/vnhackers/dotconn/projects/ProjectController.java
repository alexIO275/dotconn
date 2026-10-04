// Controller pentru proiecte + recomandări; toate rutele cer JWT, proiectele sunt vizibile doar ownerului.
package com.vnhackers.dotconn.projects;

import com.vnhackers.dotconn.user.User;
import com.vnhackers.dotconn.user.UserRepository;
import jakarta.validation.Valid;
import java.util.LinkedHashSet;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/projects")
public class ProjectController {
  private final ProjectRepository projects;
  private final UserRepository users;
  private final RecommendationService recommendations;

  public ProjectController(
      ProjectRepository projects, UserRepository users, RecommendationService recommendations) {
    this.projects = projects;
    this.users = users;
    this.recommendations = recommendations;
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  public ProjectDto create(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody CreateProjectRequest req) {
    Long userId = currentUserId(jwt);
    User owner =
        users
            .findById(userId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Contul nu a fost găsit."));

    Project project = new Project(owner);
    project.setTitle(req.title().trim());
    project.setDescription(req.description().trim());
    project.setSummary(req.summary() == null || req.summary().isBlank() ? null : req.summary().trim());
    project.setRoles(cleanStrings(req.roles()));
    project.setTasks(cleanStrings(req.tasks()));
    project.setExistingStack(req.existingStack() == null ? List.of() : cleanStrings(req.existingStack()));
    List<String> required =
        req.requiredTechnologies() == null ? List.of() : cleanStrings(req.requiredTechnologies());
    if (required.isEmpty() && !project.getExistingStack().isEmpty()) {
      required = List.copyOf(project.getExistingStack());
    }
    project.setRequiredTechnologies(required);
    project.setMaxHourlyRate(req.maxHourlyRate());
    return ProjectDto.from(projects.save(project));
  }

  @GetMapping
  public Page<ProjectDto> list(
      @AuthenticationPrincipal Jwt jwt,
      @PageableDefault(size = 20, sort = "id") Pageable pageable) {
    return projects.findByOwnerId(currentUserId(jwt), pageable).map(ProjectDto::from);
  }

  @GetMapping("/{id}")
  public ProjectDto getById(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id) {
    return projects
        .findByIdAndOwnerId(id, currentUserId(jwt))
        .map(ProjectDto::from)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Proiectul nu a fost găsit."));
  }

  @GetMapping("/{id}/recommendations")
  public List<RecommendationDto> recommendations(
      @AuthenticationPrincipal Jwt jwt,
      @PathVariable Long id,
      @RequestParam(defaultValue = "10") int limit) {
    Project project =
        projects
            .findByIdAndOwnerId(id, currentUserId(jwt))
            .orElseThrow(
                () -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Proiectul nu a fost găsit."));
    return recommendations.recommend(project, limit);
  }

  // Doar proprietarul poate edita proiectul (inclusiv linkul de repository).
  @PatchMapping("/{id}")
  public ProjectDto update(
      @AuthenticationPrincipal Jwt jwt,
      @PathVariable Long id,
      @Valid @RequestBody UpdateProjectRequest req) {
    Project project =
        projects
            .findByIdAndOwnerId(id, currentUserId(jwt))
            .orElseThrow(
                () -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Proiectul nu a fost găsit."));
    if (req.title() != null) {
      String title = req.title().trim();
      if (title.isEmpty()) {
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Titlul nu poate fi gol.");
      }
      project.setTitle(title);
    }
    if (req.description() != null) {
      project.setDescription(req.description().trim());
    }
    if (req.summary() != null) {
      String summary = req.summary().trim();
      project.setSummary(summary.isEmpty() ? null : summary);
    }
    if (req.repositoryUrl() != null) {
      String url = req.repositoryUrl().trim();
      project.setRepositoryUrl(url.isEmpty() ? null : url);
    }
    if (req.maxHourlyRate() != null) {
      project.setMaxHourlyRate(req.maxHourlyRate());
    }
    return ProjectDto.from(projects.save(project));
  }

  private static Long currentUserId(Jwt jwt) {
    try {
      return Long.valueOf(jwt.getSubject());
    } catch (NumberFormatException e) {
      throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Sesiune invalidă.");
    }
  }

  private static List<String> cleanStrings(List<String> values) {
    var cleaned = new LinkedHashSet<String>();
    for (String value : values) {
      if (value == null) {
        continue;
      }
      String trimmed = value.trim();
      if (!trimmed.isEmpty()) {
        cleaned.add(trimmed); 
      }
    }
    return List.copyOf(cleaned);
  }
}
