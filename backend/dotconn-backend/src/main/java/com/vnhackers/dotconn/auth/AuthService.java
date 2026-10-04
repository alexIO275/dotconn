package com.vnhackers.dotconn.auth;

import com.vnhackers.dotconn.user.User;
import com.vnhackers.dotconn.user.UserRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class AuthService {
  private final UserRepository users;
  private final PasswordEncoder passwordEncoder;
  private final TokenService tokens;

  public AuthService(UserRepository users, PasswordEncoder passwordEncoder, TokenService tokens) {
    this.users = users;
    this.passwordEncoder = passwordEncoder;
    this.tokens = tokens;
  }

  public AuthResponse login(LoginRequest req) {
    User user = users.findByEmail(req.email().trim().toLowerCase())
        .filter(u -> passwordEncoder.matches(req.password(), u.getPasswordHash()))
        .orElseThrow(() -> new BadCredentialsException("Invalid email or password"));
    return tokens.issue(user);
  }

  public AuthResponse signup(SignupRequest req) {
    String email = req.email().trim().toLowerCase();
    if (users.existsByEmail(email)) throw new EmailAlreadyUsedException();
    try {
      User user = users.saveAndFlush(new User(email, passwordEncoder.encode(req.password())));
      return tokens.issue(user);
    } catch (DataIntegrityViolationException e) {
      // Another request registered the same email between the check and the insert
      throw new EmailAlreadyUsedException();
    }
  }
}
