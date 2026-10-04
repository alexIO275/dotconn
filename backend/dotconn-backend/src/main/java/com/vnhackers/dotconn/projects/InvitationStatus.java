// Stările unei invitații într-un proiect (în așteptare, acceptată, refuzată).
package com.vnhackers.dotconn.projects;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

public enum InvitationStatus {
  PENDING("pending"),
  ACCEPTED("accepted"),
  DECLINED("declined");

  private final String slug;

  InvitationStatus(String slug) {
    this.slug = slug;
  }

  @JsonValue
  public String slug() {
    return slug;
  }

  @JsonCreator
  public static InvitationStatus fromSlug(String slug) {
    if (slug == null) {
      return null;
    }
    for (InvitationStatus status : values()) {
      if (status.slug.equalsIgnoreCase(slug.trim())) {
        return status;
      }
    }
    throw new IllegalArgumentException(
        "Status invalid. Valori acceptate: pending, accepted, declined.");
  }
}
