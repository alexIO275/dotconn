package com.vnhackers.dotconn.config;

import com.vnhackers.dotconn.user.User;
import com.vnhackers.dotconn.user.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
public class UserSeeder implements CommandLineRunner {
  private final UserRepository users;
  private final PasswordEncoder passwordEncoder;
  private final String email;
  private final String password;

  public UserSeeder(UserRepository users, PasswordEncoder passwordEncoder,
                    @Value("${app.seed.email:}") String email,
                    @Value("${app.seed.password:}") String password) {
    this.users = users;
    this.passwordEncoder = passwordEncoder;
    this.email = email;
    this.password = password;
  }

  @Override
  public void run(String... args) {
    if (email.isBlank() || password.isBlank()) return;
    String normalized = email.trim().toLowerCase();
    if (users.existsByEmail(normalized)) return;
    users.save(new User(normalized, passwordEncoder.encode(password)));
  }
}
