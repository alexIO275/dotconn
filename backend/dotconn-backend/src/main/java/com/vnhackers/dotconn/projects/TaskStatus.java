// Stările unei sarcini din workspace-ul unui proiect.
package com.vnhackers.dotconn.projects;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

public enum TaskStatus {
  TODO("todo"),
  IN_PROGRESS("in-progress"),
  DONE("done");

  private final String slug;

  TaskStatus(String slug) {
    this.slug = slug;
  }

  @JsonValue
  public String slug() {
    return slug;
  }

  @JsonCreator
  public static TaskStatus fromSlug(String slug) {
    if (slug == null) {
      return null;
    }
    for (TaskStatus status : values()) {
      if (status.slug.equalsIgnoreCase(slug.trim())) {
        return status;
      }
    }
    throw new IllegalArgumentException(
        "Status invalid. Valori acceptate: todo, in-progress, done.");
  }
}
