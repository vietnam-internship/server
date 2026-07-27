package com.fptis.intern.server.presentation.dev.dto;

import com.fptis.intern.server.domain.user.User;
import com.fptis.intern.server.presentation.auth.dto.UserSummaryResponse;

public record DevTokenResponse(String accessToken, String tokenType, long expiresIn, UserSummaryResponse user) {

    public static DevTokenResponse of(String accessToken, long accessTokenExpireTimeMillis, User user) {
        return new DevTokenResponse(accessToken, "Bearer", accessTokenExpireTimeMillis / 1000,
                UserSummaryResponse.from(user));
    }
}
