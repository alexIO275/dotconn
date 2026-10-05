package com.vnhackers.dotconn.config;

import com.vnhackers.dotconn.chat.ChatMessage;
import com.vnhackers.dotconn.chat.ChatMessageRepository;
import com.vnhackers.dotconn.chat.ProjectChatMessage;
import com.vnhackers.dotconn.chat.ProjectChatRepository;
import com.vnhackers.dotconn.developers.Availability;
import com.vnhackers.dotconn.developers.DeveloperProfile;
import com.vnhackers.dotconn.developers.DeveloperProfileRepository;
import com.vnhackers.dotconn.developers.DeveloperRole;
import com.vnhackers.dotconn.projects.Invitation;
import com.vnhackers.dotconn.projects.InvitationRepository;
import com.vnhackers.dotconn.projects.InvitationStatus;
import com.vnhackers.dotconn.projects.Project;
import com.vnhackers.dotconn.projects.ProjectRepository;
import com.vnhackers.dotconn.projects.ProjectTask;
import com.vnhackers.dotconn.projects.ProjectTaskRepository;
import com.vnhackers.dotconn.projects.TaskStatus;
import com.vnhackers.dotconn.user.User;
import com.vnhackers.dotconn.user.UserRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Synthetic, opt-in local demo records; never enabled by the normal application profile. */
@Component
@Profile({"local", "mysql-local"})
@ConditionalOnProperty(name = "app.demo.enabled", havingValue = "true")
public class DemoDataSeeder implements CommandLineRunner {
  static final String VERSION = "microcrew-demo-v1";
  static final String PASSWORD = "DemoCrew2026!";
  static final String DOMAIN = "@demo.microcrew.test";
  private static final Logger log = LoggerFactory.getLogger(DemoDataSeeder.class);

  private final UserRepository users;
  private final DeveloperProfileRepository profiles;
  private final ProjectRepository projects;
  private final InvitationRepository invitations;
  private final ProjectTaskRepository tasks;
  private final ChatMessageRepository messages;
  private final ProjectChatRepository projectMessages;
  private final DemoSeedRunRepository seedRuns;
  private final PasswordEncoder passwordEncoder;

  public DemoDataSeeder(
      UserRepository users,
      DeveloperProfileRepository profiles,
      ProjectRepository projects,
      InvitationRepository invitations,
      ProjectTaskRepository tasks,
      ChatMessageRepository messages,
      ProjectChatRepository projectMessages,
      DemoSeedRunRepository seedRuns,
      PasswordEncoder passwordEncoder) {
    this.users = users;
    this.profiles = profiles;
    this.projects = projects;
    this.invitations = invitations;
    this.tasks = tasks;
    this.messages = messages;
    this.projectMessages = projectMessages;
    this.seedRuns = seedRuns;
    this.passwordEncoder = passwordEncoder;
  }

  @Override
  @Transactional
  public void run(String... args) {
    if (seedRuns.existsById(VERSION)) {
      return;
    }

    List<DemoDeveloper> developers = List.of(
        developer("ana.frontend", "Ana Popescu", DeveloperRole.FRONTEND, 4, "28", Availability.AVAILABLE, "TypeScript", "React", "Vite", "CSS"),
        developer("radu.backend", "Radu Ionescu", DeveloperRole.BACKEND, 5, "32", Availability.AVAILABLE, "Java", "Spring", "MySQL", "REST"),
        developer("ioana.frontend", "Ioana Marin", DeveloperRole.FRONTEND, 3, "24", Availability.PARTIALLY_AVAILABLE, "TypeScript", "React", "Next.js"),
        developer("andrei.backend", "Andrei Pavel", DeveloperRole.BACKEND, 4, "27", Availability.AVAILABLE, "Node.js", "TypeScript", "PostgreSQL", "REST"),
        developer("alex.fullstack", "Alex Dumitru", DeveloperRole.FULL_STACK, 6, "38", Availability.AVAILABLE, "TypeScript", "React", "Java", "Spring", "MySQL"),
        developer("mara.mobile", "Mara Stan", DeveloperRole.MOBILE, 3, "30", Availability.AVAILABLE, "React Native", "TypeScript", "Expo"),
        developer("vlad.devops", "Vlad Georgescu", DeveloperRole.DEVOPS, 5, "35", Availability.PARTIALLY_AVAILABLE, "Docker", "AWS", "GitHub Actions", "Linux"),
        developer("elena.qa", "Elena Dobre", DeveloperRole.QA, 4, "22", Availability.AVAILABLE, "Playwright", "JUnit", "Postman", "TypeScript"),
        developer("mihai.data", "Mihai Rusu", DeveloperRole.DATA, 5, "34", Availability.AVAILABLE, "Python", "SQL", "Pandas", "PostgreSQL"),
        developer("daria.security", "Daria Matei", DeveloperRole.SECURITY, 6, "45", Availability.UNAVAILABLE, "OWASP", "Java", "Spring", "OAuth2"));

    List<String> accountNames = new java.util.ArrayList<>(List.of("client", "studio"));
    accountNames.addAll(developers.stream().map(DemoDeveloper::account).toList());
    for (String account : accountNames) {
      if (users.existsByEmail(account + DOMAIN)) {
        throw new IllegalStateException(
            "Demo seed oprit: există deja un cont rezervat " + account + DOMAIN
                + ". Datele existente nu au fost modificate. Dezactivează MICROCREW_DEMO_ENABLED.");
      }
    }

    Map<String, User> accounts = new LinkedHashMap<>();
    String passwordHash = passwordEncoder.encode(PASSWORD);
    for (String account : accountNames) {
      accounts.put(account, users.save(new User(account + DOMAIN, passwordHash)));
    }
    for (String account : List.of("client", "studio")) {
      DeveloperProfile clientProfile = new DeveloperProfile(accounts.get(account));
      clientProfile.setDisplayName(account.equals("client") ? "Sofia · Client" : "Studio North · Client");
      clientProfile.setDescription("Cont client fictiv pentru demonstrarea proiectelor și comunicării cu echipa.");
      profiles.save(clientProfile);
    }
    for (DemoDeveloper developer : developers) {
      DeveloperProfile profile = new DeveloperProfile(accounts.get(developer.account()));
      profile.setDisplayName(developer.name());
      profile.setRole(developer.role());
      profile.setDescription("Profil demonstrativ. " + developer.name()
          + " lucrează la proiecte " + developer.role().slug()
          + ", comunică prin chat și livrează prin branch-uri și pull request-uri.");
      profile.setTechnologies(developer.technologies());
      profile.setExperienceYears(developer.experience());
      profile.setAvailability(developer.availability());
      profile.setHourlyRate(new BigDecimal(developer.rate()));
      profiles.save(profile);
    }

    User client = accounts.get("client");
    User studio = accounts.get("studio");
    User ana = accounts.get("ana.frontend");
    User radu = accounts.get("radu.backend");

    Project ready = project(client, "[Demo] Shop local — găsește echipa",
        "Un magazin online pentru producători locali: catalog, coș, conturi și dashboard de comenzi. Caut o echipă frontend și backend care poate începe săptămâna aceasta.",
        "E-commerce MVP, gata pentru propunerea unei echipe.", List.of("frontend", "backend"),
        List.of("TypeScript", "React", "Java", "Spring", "MySQL"), "40");
    ready.setTasks(List.of("Catalog și coș responsive", "API produse și comenzi", "Autentificare și integrare frontend/backend"));
    projects.save(ready);

    Project pending = project(client, "[Demo] Booking Hub — invitații în așteptare",
        "Platformă de rezervări pentru studiouri locale. Clientul a selectat o echipă și așteaptă confirmarea frontendului și backendului.",
        "Demonstrează acceptarea și refuzul invitațiilor.", List.of("frontend", "backend"),
        List.of("TypeScript", "React", "Java", "Spring", "MySQL"), "40");
    projects.save(pending);
    invite(pending, ana, InvitationStatus.PENDING, "frontend");
    invite(pending, radu, InvitationStatus.PENDING, "backend");
    invite(pending, accounts.get("alex.fullstack"), InvitationStatus.DECLINED, "backend");

    Project active = project(client, "[Demo] LaunchBoard — workspace activ",
        "Dashboard comun pentru lansarea unui produs SaaS. Echipa confirmată construiește interfața, API-ul și integrarea într-un repository comun.",
        "Echipă completă, sarcini atribuite și conversații demo.", List.of("frontend", "backend"),
        List.of("TypeScript", "React", "Java", "Spring", "MySQL"), "40");
    active.setRepositoryUrl("https://github.com/alexl0275/dotconn");
    active.setApiContract("GET /api/projects → listă de proiecte\nPOST /api/projects → creează proiect\nGET /api/projects/{id}/workspace → membri și sarcini\nToate cererile folosesc Authorization: Bearer <token>.\nFrontend: Ana. Backend: Radu. Payload-urile se confirmă în chat înainte de modificări.");
    projects.save(active);
    invite(active, ana, InvitationStatus.ACCEPTED, "frontend");
    invite(active, radu, InvitationStatus.ACCEPTED, "backend");
    task(active, ana, "Construiește dashboard-ul proiectului", "Carduri de progres, membri și navigare spre workspace.", TaskStatus.DONE);
    task(active, radu, "Livrează endpoint-urile pentru sarcini", "CRUD pentru sarcini cu validarea accesului membrilor.", TaskStatus.IN_PROGRESS);
    task(active, ana, "Leagă interfața la API-ul de workspace", "Folosește contractul API și afișează stările de încărcare și eroare.", TaskStatus.IN_PROGRESS);
    task(active, null, "Verifică fluxul complet înainte de demo", "Login client → workspace → actualizare sarcină → mesaj către coleg.", TaskStatus.TODO);

    Project shortage = project(studio, "[Demo] SecurePay — buget insuficient",
        "Audit de securitate și integrare backend pentru plăți. Bugetul demonstrativ este prea mic pentru o echipă eligibilă, astfel aplicația trebuie să explice rolurile lipsă.",
        "Caz demonstrativ fără echipă completă disponibilă.", List.of("backend", "security"),
        List.of("Java", "Spring", "OWASP"), "15");
    projects.save(shortage);

    message(client, ana, "Bună, Ana! Pentru LaunchBoard avem nevoie de un dashboard simplu și clar. Am pus cerințele în workspace.");
    message(ana, client, "Salut! Dashboard-ul este gata. Urmează integrarea cu endpoint-urile lui Radu.");
    message(radu, ana, "Salut, Ana! GET /api/projects/{id}/workspace întoarce membrii și sarcinile. Contractul este în proiect.");
    message(ana, radu, "Perfect. Încep integrarea și îți trimit un pull request pentru review.");
    message(radu, client, "API-ul pentru sarcini este în lucru. Putem demonstra deja membri, repository și actualizarea statusurilor.");
    message(studio, accounts.get("vlad.devops"), "Salut, Vlad! Pentru următoarea lansare ne trebuie o configurare Docker și CI. Ai disponibilitate săptămâna viitoare?");
    projectMessages.save(new ProjectChatMessage(active, client, "Bun venit în LaunchBoard! Aici discutăm împreună, iar sarcinile și contractul API sunt în workspace."));
    projectMessages.save(new ProjectChatMessage(active, radu, "Endpoint-urile și payload-urile sunt descrise în contract. Pentru orice schimbare las un mesaj aici înainte de implementare."));
    projectMessages.save(new ProjectChatMessage(active, ana, "Dashboard-ul este gata. Lucrez pe branch-ul feature/workspace-ui și integrez API-ul azi."));

    seedRuns.saveAndFlush(new DemoSeedRun(VERSION));
    log.info("Demo MicroCrew creat: 2 clienți, 10 programatori, 4 proiecte. Conturi și pași în DEMO.md.");
  }

  private static DemoDeveloper developer(String account, String name, DeveloperRole role,
      int experience, String rate, Availability availability, String... technologies) {
    return new DemoDeveloper(account, name, role, experience, rate, availability, List.of(technologies));
  }

  private Project project(User owner, String title, String description, String summary,
      List<String> roles, List<String> technologies, String maxRate) {
    Project project = new Project(owner);
    project.setTitle(title);
    project.setDescription(description);
    project.setSummary(summary);
    project.setRoles(roles);
    project.setRequiredTechnologies(technologies);
    project.setExistingStack(List.of("Vite", "TypeScript", "Spring", "MySQL"));
    project.setMaxHourlyRate(new BigDecimal(maxRate));
    return project;
  }

  private void invite(Project project, User invitee, InvitationStatus status, String role) {
    Invitation invitation = new Invitation(project, project.getOwner(), invitee);
    invitation.setStatus(status);
    invitation.setAssignedRole(role);
    if (status != InvitationStatus.PENDING) {
      invitation.setRespondedAt(Instant.now());
    }
    invitations.save(invitation);
  }

  private void task(Project project, User assignee, String title, String description, TaskStatus status) {
    ProjectTask task = new ProjectTask(project, project.getOwner());
    task.setTitle(title);
    task.setDescription(description);
    task.setAssignee(assignee);
    task.setStatus(status);
    tasks.save(task);
  }

  private void message(User sender, User recipient, String content) {
    messages.save(new ChatMessage(sender, recipient, content));
  }

  private record DemoDeveloper(String account, String name, DeveloperRole role, int experience,
      String rate, Availability availability, List<String> technologies) {}
}
