package com.fptis.intern.server.presentation.auth;

import com.fptis.intern.server.application.auth.GoogleAuthService;
import com.fptis.intern.server.application.auth.GoogleAuthorizationCodeExchanger;
import com.fptis.intern.server.application.auth.LoginResult;
import com.fptis.intern.server.application.auth.RefreshTokenService;
import com.fptis.intern.server.domain.auth.IssuedRefreshToken;
import com.fptis.intern.server.domain.user.User;
import com.fptis.intern.server.domain.user.UserRepository;
import com.fptis.intern.server.global.annotation.PublicApi;
import com.fptis.intern.server.global.exception.ApiResponse;
import com.fptis.intern.server.global.exception.BusinessErrorCode;
import com.fptis.intern.server.global.exception.BusinessException;
import com.fptis.intern.server.global.util.CookieUtil;
import com.fptis.intern.server.global.util.JwtProvider;
import com.fptis.intern.server.presentation.auth.dto.GoogleLoginResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private final JwtProvider jwtProvider;
    private final CookieUtil cookieUtil;
    private final UserRepository userRepository;
    private final RefreshTokenService refreshTokenService;
    private final GoogleAuthService googleAuthService;
    private final GoogleAuthorizationCodeExchanger googleAuthorizationCodeExchanger;

    /**
     * 프론트 AuthCallbackPage가 자기 URL에서 code/state를 받아 state를 검증한 뒤 호출하는 엔드포인트.
     * ApiResponse로 감싸지 않고 GoogleLoginResponse를 그대로 반환한다 — client/http.ts가 바디를 그대로 파싱한다.
     */
    @PublicApi
    @GetMapping("/google/callback")
    public GoogleLoginResponse googleCallback(@RequestParam String code, HttpServletResponse response) {
        String idToken = googleAuthorizationCodeExchanger.exchangeForIdToken(code);
        LoginResult result = googleAuthService.loginWithGoogle(idToken);

        cookieUtil.addRefreshTokenCookie(response, result.refreshToken(), RefreshTokenService.REFRESH_TOKEN_EXPIRE_TIME);

        return GoogleLoginResponse.of(result, jwtProvider.getAccessTokenExpireTime());
    }

    @PublicApi
    @PostMapping("/reissue")
    public ApiResponse<?> reissue(HttpServletRequest request, HttpServletResponse response) {
        String presentedRefreshToken = cookieUtil.getRefreshTokenFromRequest(request);
        if (presentedRefreshToken == null) {
            cookieUtil.clearTokenCookies(response);
            throw new BusinessException(BusinessErrorCode.UNAUTHORIZED);
        }

        IssuedRefreshToken rotated;
        try {
            rotated = refreshTokenService.rotate(presentedRefreshToken);
        } catch (BusinessException e) {
            cookieUtil.clearTokenCookies(response);
            throw e;
        }

        User user = userRepository.findById(rotated.userId())
                .orElseThrow(() -> new BusinessException(BusinessErrorCode.UNAUTHORIZED));

        String newAccessToken = jwtProvider.createAccessToken(user.getId(), user.getRole());
        cookieUtil.addRefreshTokenCookie(response, rotated.rawToken(), RefreshTokenService.REFRESH_TOKEN_EXPIRE_TIME);

        log.debug("[AuthController] reissued tokens for userId={}", user.getId());
        return ApiResponse.success(GoogleLoginResponse.of(
                new LoginResult(newAccessToken, rotated.rawToken(), false, user),
                jwtProvider.getAccessTokenExpireTime()));
    }

    @PublicApi
    @PostMapping("/logout")
    public ApiResponse<?> logout(HttpServletRequest request, HttpServletResponse response) {
        String presentedRefreshToken = cookieUtil.getRefreshTokenFromRequest(request);
        refreshTokenService.revoke(presentedRefreshToken);
        cookieUtil.clearTokenCookies(response);
        return ApiResponse.success();
    }
}
