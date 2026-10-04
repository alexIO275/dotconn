package com.example.codematch.auth;

public record AuthResponse(String token, long expiresInSeconds) {}
