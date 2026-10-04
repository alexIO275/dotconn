package com.vnhackers.dotconn.auth;

public record AuthResponse(String token, long expiresInSeconds) {}
