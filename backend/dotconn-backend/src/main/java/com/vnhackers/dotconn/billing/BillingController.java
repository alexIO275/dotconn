package com.vnhackers.dotconn.billing;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/billing")
public class BillingController {
  private final BillingService service;
  public BillingController(BillingService service) { this.service=service; }
  public record CheckoutRequest(@NotBlank @Pattern(regexp="bronze|silver|gold") String plan) {}
  public record SyncRequest(@NotBlank @Pattern(regexp="cs_test_[A-Za-z0-9]+") String sessionId) {}
  @GetMapping("/plans") public BillingService.PlansDto plans() { return service.plans(); }
  @GetMapping("/subscription") public BillingSubscriptionDto subscription(@AuthenticationPrincipal Jwt jwt) { return service.current(userId(jwt)); }
  @PostMapping("/checkout") public BillingService.UrlDto checkout(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody CheckoutRequest request) { return service.checkout(userId(jwt), request.plan()); }
  @PostMapping("/portal") public BillingService.UrlDto portal(@AuthenticationPrincipal Jwt jwt) { return service.portal(userId(jwt)); }
  @PostMapping("/sync") public BillingSubscriptionDto sync(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody SyncRequest request) { return service.sync(userId(jwt), request.sessionId()); }
  @PostMapping("/webhook") public java.util.Map<String,Boolean> webhook(@RequestBody String rawBody,
      @RequestHeader(value="Stripe-Signature", required=false) String signature) { return service.webhook(rawBody, signature); }
  private static Long userId(Jwt jwt) {
    try { return Long.valueOf(jwt.getSubject()); }
    catch (NullPointerException | NumberFormatException e) { throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Sesiune invalidă."); }
  }
}
