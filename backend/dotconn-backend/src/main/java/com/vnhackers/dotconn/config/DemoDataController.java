package com.vnhackers.dotconn.config;

import com.vnhackers.dotconn.developers.DeveloperProfileRepository;
import com.vnhackers.dotconn.user.UserRepository;
import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/** Public descriptor only when the opt-in local demo is enabled; never returns tokens or hashes. */
@RestController
@Profile({"local", "mysql-local"})
@ConditionalOnProperty(name = "app.demo.enabled", havingValue = "true")
public class DemoDataController {
  private final UserRepository users;
  private final DeveloperProfileRepository profiles;
  private final DemoSeedRunRepository seedRuns;

  public DemoDataController(UserRepository users, DeveloperProfileRepository profiles,
      DemoSeedRunRepository seedRuns) {
    this.users = users;
    this.profiles = profiles;
    this.seedRuns = seedRuns;
  }

  @GetMapping("/api/demo")
  public DemoDescriptor descriptor() {
    if (!seedRuns.existsById(DemoDataSeeder.VERSION)) {
      throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Datele demo se pregătesc.");
    }
    List<DemoAccount> accounts = List.of("client", "studio", "ana.frontend", "radu.backend",
        "ioana.frontend", "andrei.backend", "alex.fullstack", "mara.mobile", "vlad.devops",
        "elena.qa", "mihai.data", "daria.security").stream()
        .map(account -> users.findByEmail(account + DemoDataSeeder.DOMAIN))
        .flatMap(java.util.Optional::stream)
        .map(user -> profiles.findById(user.getId())
            .map(profile -> new DemoAccount(user.getEmail(), profile.getDisplayName(),
                profile.getRole() == null ? "client" : profile.getRole().slug()))
            .orElse(new DemoAccount(user.getEmail(), user.getEmail(), "client")))
        .toList();
    return new DemoDescriptor(accounts);
  }

  public record DemoDescriptor(List<DemoAccount> accounts) {}
  public record DemoAccount(String email, String name, String role) {}
}
