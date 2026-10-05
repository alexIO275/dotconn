package com.vnhackers.dotconn.projects;

import com.vnhackers.dotconn.developers.Availability;
import com.vnhackers.dotconn.developers.DeveloperProfile;
import com.vnhackers.dotconn.developers.DeveloperProfileRepository;
import com.vnhackers.dotconn.developers.DeveloperRole;
import java.util.ArrayList;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.LockModeType;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/** Complete teams with one distinct person per role. No partial team ever becomes a batch invite. */
@Service
public class TeamAssemblyService {
  private static final int ALTERNATIVES = 3;
  private final DeveloperProfileRepository profiles;
  private final InvitationRepository invitations;
  private final ProjectRepository projects;
  private final ProjectAccessService access;
  @PersistenceContext private EntityManager entityManager;

  public TeamAssemblyService(DeveloperProfileRepository profiles, InvitationRepository invitations,
      ProjectRepository projects, ProjectAccessService access) {
    this.profiles = profiles;
    this.invitations = invitations;
    this.projects = projects;
    this.access = access;
  }

  @Transactional(readOnly = true)
  public TeamProposalsDto proposals(Long projectId, Long ownerId) {
    return proposalsFor(access.requireOwner(projectId, ownerId), 0);
  }

  private TeamProposalsDto proposalsFor(Project project, int minScore) {
    List<String> roles = requiredRoles(project);
    Set<Long> accepted = acceptedIds(project.getId());
    List<DeveloperProfile> candidates = profiles.findAllWithTechnologies().stream()
        .filter(p -> !p.getId().equals(project.getOwner().getId()))
        .filter(p -> eligible(p, project, accepted.contains(p.getId())))
        .sorted(Comparator.comparing(DeveloperProfile::getId))
        .toList();
    Map<Integer, List<State>> states = new HashMap<>();
    states.put(0, List.of(new State(new TeamProposalsDto.Member[roles.size()])));

    // Iterate each person once. Every transition reads the previous snapshot, so a full-stack
    // developer cannot fill two roles. Retain three distinct person sets per role mask.
    for (DeveloperProfile profile : candidates) {
      Map<Integer, List<State>> next = new HashMap<>(states);
      for (var entry : states.entrySet()) {
        for (int roleIndex = 0; roleIndex < roles.size(); roleIndex++) {
          int bit = 1 << roleIndex;
          String role = roles.get(roleIndex);
          if ((entry.getKey() & bit) != 0 || !coversRole(profile.getRole(), role)) continue;
          var member = member(profile, project, role, accepted.contains(profile.getId()));
          if (!member.existingMember() && member.score() < minScore) continue;
          int mask = entry.getKey() | bit;
          List<State> additions = new ArrayList<>(next.getOrDefault(mask, List.of()));
          for (State current : entry.getValue()) {
            var assignments = current.members().clone();
            assignments[roleIndex] = member;
            additions.add(new State(assignments));
          }
          next.put(mask, bestDistinct(additions));
        }
      }
      states = next;
    }

    int completeMask = (1 << roles.size()) - 1;
    List<State> complete = states.getOrDefault(completeMask, List.of());
    if (!complete.isEmpty()) {
      if (complete.getFirst().existingCount() == roles.size()) {
        return new TeamProposalsDto("complete", roles, List.of(), List.of(),
            "Membrii care au acceptat acoperă deja toate rolurile proiectului.");
      }
      return new TeamProposalsDto("ready", roles, List.of(),
          complete.stream().map(State::dto).toList(),
          "Alege un pachet complet. Fiecare programator trebuie să accepte invitația separat.");
    }
    int bestMask = states.entrySet().stream()
        .max(Comparator.<Map.Entry<Integer, List<State>>>comparingInt(e -> Integer.bitCount(e.getKey()))
            .thenComparing(e -> e.getValue().getFirst(), STATE_ORDER.reversed()))
        .map(Map.Entry::getKey).orElse(0);
    List<String> missing = new ArrayList<>();
    for (int i = 0; i < roles.size(); i++) if ((bestMask & (1 << i)) == 0) missing.add(roles.get(i));
    return new TeamProposalsDto("unavailable", roles, missing, List.of(),
        "Nu există încă o echipă completă cu persoane distincte, disponibile și în limita de tarif.");
  }

  @Transactional
  public TeamInvitationResult inviteTeam(Long projectId, Long ownerId, TeamInvitationRequest request) {
    Project project = lockOwnedProject(projectId, ownerId);
    return inviteValidated(project, request);
  }

  // Retains the old endpoint, but it now invites only the best COMPLETE package.
  @Transactional
  public List<InvitationDto> autoAssemble(Long projectId, Long ownerId, int maxInvitations, int minScore) {
    Project project = lockOwnedProject(projectId, ownerId);
    if (requiredRoles(project).size() > maxInvitations) {
      throw conflict("Limita de invitații nu poate acoperi toate rolurile. Mărește limita.");
    }
    var proposed = proposalsFor(project, minScore);
    if (proposed.status().equals("complete")) return List.of();
    if (proposed.teams().isEmpty()) return List.of();
    var assignments = proposed.teams().getFirst().members().stream()
        .map(m -> new TeamInvitationRequest.Assignment(m.role(), m.developerId())).toList();
    Set<Long> alreadyPending = invitations.findByProjectIdAndStatus(projectId, InvitationStatus.PENDING)
        .stream().map(i -> i.getInvitee().getId()).collect(Collectors.toSet());
    return inviteValidated(project, new TeamInvitationRequest(assignments)).invitations().stream()
        .filter(i -> !alreadyPending.contains(i.inviteeId())).toList();
  }

  private TeamInvitationResult inviteValidated(Project project, TeamInvitationRequest request) {
    List<String> roles = requiredRoles(project);
    Set<String> assignedRoles = new HashSet<>();
    Set<Long> personIds = new HashSet<>();
    if (request.assignments().size() != roles.size()) {
      throw conflict("Pachetul trebuie să acopere exact toate rolurile proiectului.");
    }
    for (var assignment : request.assignments()) {
      if (!roles.contains(assignment.role()) || !assignedRoles.add(assignment.role())) {
        throw conflict("Fiecare rol cerut trebuie acoperit o singură dată.");
      }
      if (!personIds.add(assignment.developerId())) {
        throw conflict("Un programator nu poate ocupa două locuri în același pachet.");
      }
      if (assignment.developerId().equals(project.getOwner().getId())) {
        throw conflict("Proprietarul nu poate fi invitat în propria echipă.");
      }
    }
    Set<Long> accepted = acceptedIds(project.getId());
    Map<Long, DeveloperProfile> locked = new HashMap<>();
    // Stable lock order prevents deadlocks between different projects involving the same people.
    for (Long personId : personIds.stream().sorted().toList()) {
      var profile = profiles.findByIdForTeam(personId)
          .orElseThrow(() -> conflict("Un programator din pachet nu mai are profil disponibil."));
      entityManager.refresh(profile, LockModeType.PESSIMISTIC_READ);
      locked.put(personId, profile);
    }
    for (var assignment : request.assignments()) {
      var profile = locked.get(assignment.developerId());
      if (!coversRole(profile.getRole(), assignment.role())
          || !eligible(profile, project, accepted.contains(profile.getId()))) {
        throw conflict("Pachetul nu mai este eligibil. Actualizează propunerile de echipă.");
      }
    }
    // All validation finishes before the first write; the whole operation shares one transaction.
    List<InvitationDto> result = new ArrayList<>();
    int alreadyMembers = 0;
    for (var assignment : request.assignments()) {
      if (accepted.contains(assignment.developerId())) {
        alreadyMembers++;
        continue;
      }
      Invitation invitation = invitations.findByProjectIdAndInviteeId(project.getId(), assignment.developerId())
          .orElseGet(() -> new Invitation(project, project.getOwner(), locked.get(assignment.developerId()).getUser()));
      invitation.setAssignedRole(assignment.role());
      if (invitation.getStatus() == InvitationStatus.DECLINED) {
        invitation.setStatus(InvitationStatus.PENDING);
        invitation.setRespondedAt(null);
      }
      result.add(InvitationDto.from(invitations.save(invitation)));
    }
    return new TeamInvitationResult(List.copyOf(result), alreadyMembers);
  }

  private Project lockOwnedProject(Long projectId, Long ownerId) {
    var project = projects.findByIdForUpdate(projectId)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Proiectul nu a fost găsit."));
    if (!project.getOwner().getId().equals(ownerId)) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Proiectul nu a fost găsit.");
    }
    return project;
  }

  private Set<Long> acceptedIds(Long projectId) {
    return invitations.findByProjectIdAndStatus(projectId, InvitationStatus.ACCEPTED).stream()
        .map(i -> i.getInvitee().getId()).collect(Collectors.toSet());
  }

  private static List<String> requiredRoles(Project project) {
    return project.getRoles().stream().map(r -> r.trim().toLowerCase(Locale.ROOT)).distinct().toList();
  }

  static boolean coversRole(DeveloperRole actual, String required) {
    return actual != null && (actual.slug().equals(required)
        || (actual == DeveloperRole.FULL_STACK && (required.equals("frontend") || required.equals("backend"))));
  }

  private static boolean eligible(DeveloperProfile profile, Project project, boolean existing) {
    if (profile.getRole() == null) return false;
    if (existing) return true; // Accepted members already committed; do not invite them again.
    if (profile.getAvailability() == null || profile.getAvailability() == Availability.UNAVAILABLE) return false;
    return project.getMaxHourlyRate() == null || (profile.getHourlyRate() != null
        && profile.getHourlyRate().compareTo(project.getMaxHourlyRate()) <= 0);
  }

  private static TeamProposalsDto.Member member(DeveloperProfile profile, Project project, String role, boolean existing) {
    int rolePoints = profile.getRole().slug().equals(role) ? 40 : 32;
    Set<String> required = project.getRequiredTechnologies().stream()
        .map(s -> s.toLowerCase(Locale.ROOT)).collect(Collectors.toSet());
    Set<String> known = profile.getTechnologies().stream().map(s -> s.toLowerCase(Locale.ROOT)).collect(Collectors.toSet());
    List<String> common = required.stream().filter(known::contains).sorted().toList();
    int skills = required.isEmpty() ? 15 : (int) Math.round(30.0 * common.size() / required.size());
    int availability = profile.getAvailability() == Availability.AVAILABLE ? 20 :
        profile.getAvailability() == Availability.PARTIALLY_AVAILABLE ? 10 : 0;
    int score = rolePoints + skills + availability + (profile.getHourlyRate() == null ? 5 : 10);
    List<String> reasons = new ArrayList<>();
    reasons.add(profile.getRole().slug().equals(role) ? "Acoperă rolul " + role + "." : "Full-stack alocat pe " + role + ".");
    if (!common.isEmpty()) reasons.add("Tehnologii comune: " + String.join(", ", common) + ".");
    if (existing) reasons.add("Membru care a acceptat deja proiectul.");
    else reasons.add(availability == 20 ? "Disponibil acum." : "Parțial disponibil.");
    if (project.getMaxHourlyRate() != null && !existing) reasons.add("Tarif în limita de " + project.getMaxHourlyRate() + " pe oră.");
    return new TeamProposalsDto.Member(profile.getId(), profile.getDisplayName(), role, profile.getRole(),
        List.copyOf(profile.getTechnologies()), profile.getAvailability(), profile.getHourlyRate(), existing, score, List.copyOf(reasons));
  }

  private static final Comparator<State> STATE_ORDER = Comparator.comparingInt(State::existingCount).reversed()
      .thenComparing(Comparator.comparingInt(State::score).reversed()).thenComparing(State::signature);

  private static List<State> bestDistinct(List<State> choices) {
    Map<String, State> unique = new LinkedHashMap<>();
    choices.stream().sorted(STATE_ORDER).forEach(s -> unique.putIfAbsent(s.personSet(), s));
    return unique.values().stream().limit(ALTERNATIVES).toList();
  }

  private record State(TeamProposalsDto.Member[] members) {
    int existingCount() { return (int) Arrays.stream(members).filter(m -> m != null && m.existingMember()).count(); }
    int score() { return Arrays.stream(members).filter(m -> m != null).mapToInt(TeamProposalsDto.Member::score).sum(); }
    String signature() { return Arrays.stream(members).map(m -> m == null ? "-" : m.role() + ":" + m.developerId()).collect(Collectors.joining("|")); }
    String personSet() { return Arrays.stream(members).filter(m -> m != null).map(TeamProposalsDto.Member::developerId).sorted().map(Object::toString).collect(Collectors.joining(",")); }
    TeamProposalsDto.Team dto() {
      var selected = Arrays.stream(members).filter(m -> m != null).toList();
      return new TeamProposalsDto.Team(personSet(), (int) Math.round((double) score() / selected.size()), selected);
    }
  }

  private static ResponseStatusException conflict(String message) {
    return new ResponseStatusException(HttpStatus.CONFLICT, message);
  }
}
