package com.fptis.intern.server.application.auth;

import com.fptis.intern.server.domain.user.User;

public record LoginResult(String accessToken, String refreshToken, boolean isNewUser, User user) {
}
