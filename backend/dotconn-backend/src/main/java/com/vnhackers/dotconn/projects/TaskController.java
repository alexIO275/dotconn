// Controller pentru sarcinile workspace-ului: membrii citesc/creează/actualizează, doar proprietarul șterge.
package com.vnhackers.dotconn.projects;

import com.vnhackers.dotconn.user.User;
import com.vnhackers.dotconn.user.UserRepository;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
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
@RequestMapping("/api/projects/{id}/tasks")
public class TaskController {
  private final ProjectTaskRepository tasks;
  private final UserRepository users;
  private final ProjectAccessService access;

  public TaskController(
      ProjectTaskRepository tasks, UserRepository users, ProjectAccessService access) {
    this.tasks = tasks;
    this.users = users;
    this.access = access;
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  public TaskDto create(
      @AuthenticationPrincipal Jwt jwt,
      @PathVariable Long id,
      @Valid @RequestBody CreateTaskRequest req) {
    Long userId = currentUserId(jwt);
    Project project = access.requireMember(id, userId);
    User creator =
        users
            .findById(userId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Contul nu a fost găsit."));

    ProjectTask task = new ProjectTask(project, creator);
    task.setTitle(req.title().trim());
    task.setDescription(
        req.description() == null || req.description().isBlank() ? null : req.description().trim());
    if (req.assigneeId() != null) {
      task.setAssignee(resolveAssignee(id, req.assigneeId()));
    }
    return TaskDto.from(tasks.save(task));
  }

  @GetMapping
  public Page<TaskDto> list(
      @AuthenticationPrincipal Jwt jwt,
      @PathVariable Long id,
      @RequestParam(required = false) String status,
      @PageableDefault(size = 20, sort = "id") Pageable pageable) {
    access.requireMember(id, currentUserId(jwt));
    if (status == null || status.isBlank()) {
      return tasks.findByProjectId(id, pageable).map(TaskDto::from);
    }
    TaskStatus filter;
    try {
      filter = TaskStatus.fromSlug(status);
    } catch (IllegalArgumentException e) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
    }
    return tasks.findByProjectIdAndStatus(id, filter, pageable).map(TaskDto::from);
  }

  @PatchMapping("/{taskId}")
  public TaskDto update(
      @AuthenticationPrincipal Jwt jwt,
      @PathVariable Long id,
      @PathVariable Long taskId,
      @Valid @RequestBody UpdateTaskRequest req) {
    access.requireMember(id, currentUserId(jwt));
    ProjectTask task =
        tasks
            .findByIdAndProjectId(taskId, id)
            .orElseThrow(
                () -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Sarcina nu a fost găsită."));
    if (req.title() != null) {
      String title = req.title().trim();
      if (title.isEmpty()) {
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Titlul nu poate fi gol.");
      }
      task.setTitle(title);
    }
    if (req.description() != null) {
      String description = req.description().trim();
      task.setDescription(description.isEmpty() ? null : description);
    }
    if (req.status() != null) {
      task.setStatus(req.status());
    }
    if (req.assigneeId() != null) {
      task.setAssignee(resolveAssignee(id, req.assigneeId()));
    }
    return TaskDto.from(tasks.save(task));
  }

  @DeleteMapping("/{taskId}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void delete(
      @AuthenticationPrincipal Jwt jwt, @PathVariable Long id, @PathVariable Long taskId) {
    Long userId = currentUserId(jwt);
    Project project = access.requireMember(id, userId);
    if (!project.getOwner().getId().equals(userId)) {
      throw new ResponseStatusException(
          HttpStatus.FORBIDDEN, "Doar proprietarul poate șterge sarcini.");
    }
    ProjectTask task =
        tasks
            .findByIdAndProjectId(taskId, id)
            .orElseThrow(
                () -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Sarcina nu a fost găsită."));
    tasks.delete(task);
  }

  // Asignatul trebuie să existe și să fie proprietar sau membru (invitație acceptată).
  private User resolveAssignee(Long projectId, Long assigneeId) {
    User assignee =
        users
            .findById(assigneeId)
            .orElseThrow(
                () -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Asignatul nu a fost găsit."));
    if (!access.isOwnerOrMember(projectId, assigneeId)) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST, "Sarcina poate fi asignată doar unui membru al proiectului.");
    }
    return assignee;
  }

  private static Long currentUserId(Jwt jwt) {
    try {
      return Long.valueOf(jwt.getSubject());
    } catch (NumberFormatException e) {
      throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Sesiune invalidă.");
    }
  }
}
