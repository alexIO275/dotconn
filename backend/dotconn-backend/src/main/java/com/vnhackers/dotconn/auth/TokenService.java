package com.vnhackers.dotconn.auth;

import com.vnhackers.dotconn.user.User;
import java.time.Instant;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

@Service
public class TokenService {
  private final JwtEncoder encoder;
  private final long ttlSeconds;

  public TokenService(JwtEncoder encoder, @Value("${app.jwt.ttl-seconds}") long ttlSeconds) {
    this.encoder = encoder;
    this.ttlSeconds = ttlSeconds;
  }

  public AuthResponse issue(User user) {
    Instant now = Instant.now();
    JwtClaimsSet claims = JwtClaimsSet.builder()
        .issuer("codematch")
        .issuedAt(now)
        .expiresAt(now.plusSeconds(ttlSeconds))
        .subject(String.valueOf(user.getId()))
        .claim("email", user.getEmail())
        .build();
    JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
    String token = encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
    return new AuthResponse(token, ttlSeconds);
  }
}
