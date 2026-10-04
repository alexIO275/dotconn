@RestController
public class MeController {
  @GetMapping("/api/me")
  public Map<String, Object> me(@AuthenticationPrincipal Jwt jwt) {
    return Map.of("id", jwt.getSubject(), "email", jwt.getClaimAsString("email"));
  }
}
