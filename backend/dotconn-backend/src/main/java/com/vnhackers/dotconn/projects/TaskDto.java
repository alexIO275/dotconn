// DTO de răspuns pentru o sarcină din workspace-ul comun.
package com.vnhackers.dotconn.projects;

import java.time.Instant;

public record TaskDto(
    Long id,
    Long projectId,
    String title,
    String description,
    TaskStatus status,
    Long assigneeId,
    Long createdById,
    Instant createdAt) {
  public static TaskDto from(ProjectTask task) {
    return new TaskDto(
        task.getId(),
        task.getProject().getId(),
        task.getTitle(),
        task.getDescription(),
        task.getStatus(),
        task.getAssignee() == null ? null : task.getAssignee().getId(),
        task.getCreatedBy().getId(),
        task.getCreatedAt());
  }
}
