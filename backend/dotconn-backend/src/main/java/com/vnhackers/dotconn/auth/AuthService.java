@Service
public class AuthService {
  private final UserRepository users;
  private final Password passwordEncoder;
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
}
