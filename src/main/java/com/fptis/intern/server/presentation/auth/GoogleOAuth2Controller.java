package com.fptis.intern.server.presentation.auth;

import com.fptis.intern.server.global.annotation.PublicApi;
import com.fptis.intern.server.global.config.GoogleOAuthProperties;
import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeRequestUrl;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 프론트가 /oauth2/authorization/google로 풀 페이지 리다이렉트하는 진입점.
 * /auth 하위가 아니다 — 프론트 LoginPage.tsx가 API_BASE_URL 바로 아래로 호출한다.
 * CSRF 방지는 프론트가 세션스토리지 플래그(markOAuthStarted/consumeOAuthStarted)로 자체 처리하고
 * 백엔드에 state 값을 만들어 보내지 않으므로, state는 옵션으로 두고 값이 있을 때만 relay한다.
 */
@RestController
@RequiredArgsConstructor
public class GoogleOAuth2Controller {

    private static final List<String> SCOPES = List.of("openid", "email", "profile");

    private final GoogleOAuthProperties googleOAuthProperties;

    @PublicApi
    @GetMapping("/oauth2/authorization/google")
    public void authorize(@RequestParam(required = false) String state, HttpServletResponse response)
            throws IOException {
        GoogleAuthorizationCodeRequestUrl authorizeUrl = new GoogleAuthorizationCodeRequestUrl(
                googleOAuthProperties.clientId(),
                googleOAuthProperties.redirectUri(),
                SCOPES);
        if (state != null) {
            authorizeUrl.setState(state);
        }

        response.sendRedirect(authorizeUrl.build());
    }
}
