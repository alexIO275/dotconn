package com.vnhackers.dotconn.developers;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

public enum Availability {
  AVAILABLE("available"),
  PARTIALLY_AVAILABLE("partially-available"),
  UNAVAILABLE("unavailable");

  private final String slug;

  Availability(String slug) {
    this.slug = slug;
  }

  @JsonValue
  public String slug() {
    return slug;
  }

  @JsonCreator
  public static Availability fromSlug(String slug) {
    if (slug == null) {
      return null;
    }
    for (Availability availability : values()) {
      if (availability.slug.equalsIgnoreCase(slug.trim())) {
        return availability;
      }
    }
    throw new IllegalArgumentException(
        "Disponibilitate invalidă. Valori acceptate: available, partially-available, unavailable.");
  }
}
