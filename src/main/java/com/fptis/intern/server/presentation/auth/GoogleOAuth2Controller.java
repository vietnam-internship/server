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
 * 프론트가 /oauth2/authorization/google?state=...로 풀 페이지 리다이렉트하는 진입점.
 * /auth 하위가 아니다 — 프론트 LoginPage.tsx가 API_BASE_URL 바로 아래로 호출한다.
 * state는 프론트가 만들어 세션스토리지에 보관한 값이므로, 서버는 새로 만들지 않고 그대로 relay만 한다.
 */
@RestController
@RequiredArgsConstructor
public class GoogleOAuth2Controller {

    private static final List<String> SCOPES = List.of("openid", "email", "profile");

    private final GoogleOAuthProperties googleOAuthProperties;

    @PublicApi
    @GetMapping("/oauth2/authorization/google")
    public void authorize(@RequestParam String state, HttpServletResponse response) throws IOException {
        String authorizeUrl = new GoogleAuthorizationCodeRequestUrl(
                googleOAuthProperties.clientId(),
                googleOAuthProperties.redirectUri(),
                SCOPES)
                .setState(state)
                .build();

        response.sendRedirect(authorizeUrl);
    }
}
