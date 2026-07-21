package com.fptis.intern.server.presentation.auth.dto;

import com.fptis.intern.server.application.auth.LoginResult;

/**
 * client/src/types/index.ts의 GoogleLoginResponse와 필드명·타입이 정확히 일치해야 한다 —
 * ApiResponse로 감싸지 않고 이 레코드를 그대로 최상위 JSON으로 응답한다.
 */
public record GoogleLoginResponse(String accessToken, String tokenType, long expiresIn, boolean isNewUser,
                                   UserSummaryResponse user) {

    public static GoogleLoginResponse of(LoginResult result, long accessTokenExpireTimeMillis) {
        return new GoogleLoginResponse(
                result.accessToken(),
                "Bearer",
                accessTokenExpireTimeMillis / 1000,
                result.isNewUser(),
                UserSummaryResponse.from(result.user()));
    }
}
