package com.fptis.intern.server.domain.auth;

public record GoogleUserInfo(String googleId, String email, boolean emailVerified, String name) {
}
