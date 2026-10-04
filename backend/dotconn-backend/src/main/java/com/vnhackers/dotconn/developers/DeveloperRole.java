// Definește rolurile de programator expuse în API (aliniate cu analiza de proiect: 8 roluri).
package com.vnhackers.dotconn.developers;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

public enum DeveloperRole {
  FRONTEND("frontend"),
  BACKEND("backend"),
  FULL_STACK("full-stack"),
  MOBILE("mobile"),
  DEVOPS("devops"),
  QA("qa"),
  DATA("data"),
  SECURITY("security");
  private final String slug;

  DeveloperRole(String slug) {
    this.slug = slug;
  }

  @JsonValue
  public String slug() {
    return slug;
  }

  @JsonCreator
  public static DeveloperRole fromSlug(String slug) {
    if (slug == null) {
      return null;
    }
    for (DeveloperRole role : values()) {
      if (role.slug.equalsIgnoreCase(slug.trim())) {
        return role;
      }
    }
    throw new IllegalArgumentException("Rol invalid. Valori acceptate: frontend, backend, full-stack, mobile, devops, qa, data, security.");
  }
}
