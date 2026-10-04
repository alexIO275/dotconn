package com.vnhackers.dotconn.developers;

import jakarta.persistence.criteria.JoinType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/developers")
public class DeveloperController {

  private final DeveloperProfileRepository profiles;

  public DeveloperController(DeveloperProfileRepository profiles) {
    this.profiles = profiles;
  }

  @GetMapping
  public Page<DeveloperListDto> list(
      @RequestParam(required = false) String role,
      @RequestParam(required = false) String tech,
      @RequestParam(required = false) String availability,
      @PageableDefault(size = 20, sort = "id") Pageable pageable) {
    DeveloperRole roleFilter = parseRole(role);
    Availability availabilityFilter = parseAvailability(availability);
    String techFilter = tech == null ? null : tech.trim();

    Specification<DeveloperProfile> spec =
        (root, query, cb) -> {
          var predicates = new java.util.ArrayList<jakarta.persistence.criteria.Predicate>();
          if (roleFilter != null) {
            predicates.add(cb.equal(root.get("role"), roleFilter));
          }
          if (availabilityFilter != null) {
            predicates.add(cb.equal(root.get("availability"), availabilityFilter));
          }
          if (techFilter != null && !techFilter.isEmpty()) {
            query.distinct(true);
            var join = root.join("technologies", JoinType.INNER);
            predicates.add(
                cb.like(cb.lower(join.as(String.class)), "%" + techFilter.toLowerCase() + "%"));
          }
          return cb.and(predicates.toArray(new jakarta.persistence.criteria.Predicate[0]));
        };

    return profiles.findAll(spec, pageable).map(DeveloperListDto::from);
  }

  @GetMapping("/{id}")
  public DeveloperDetailDto getById(@PathVariable Long id) {
    return profiles
        .findById(id)
        .map(DeveloperDetailDto::from)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Profilul nu a fost găsit."));
  }

  private static DeveloperRole parseRole(String role) {
    if (role == null || role.isBlank()) {
      return null;
    }
    try {
      return DeveloperRole.fromSlug(role);
    } catch (IllegalArgumentException e) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
    }
  }

  private static Availability parseAvailability(String availability) {
    if (availability == null || availability.isBlank()) {
      return null;
    }
    try {
      return Availability.fromSlug(availability);
    } catch (IllegalArgumentException e) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
    }
  }
}
