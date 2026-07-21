package com.fptis.intern.server.application.auth;

import com.fptis.intern.server.domain.auth.GoogleUserInfo;
import com.fptis.intern.server.global.config.GoogleOAuthProperties;
import com.fptis.intern.server.global.exception.BusinessErrorCode;
import com.fptis.intern.server.global.exception.BusinessException;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.Collections;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * GoogleIdTokenVerifier가 JWKS 캐싱·aud·iss·exp·서명을 전부 처리한다 — 여기서는
 * client id 하나만 audience로 지정하고 payload를 우리 도메인 타입으로 변환한다.
 */
@Component
@RequiredArgsConstructor
public class GoogleIdTokenVerifierAdapter implements GoogleTokenVerifier {

    private final GoogleOAuthProperties googleOAuthProperties;

    private GoogleIdTokenVerifier verifier;

    @PostConstruct
    void init() {
        this.verifier = new GoogleIdTokenVerifier.Builder(new NetHttpTransport(), GsonFactory.getDefaultInstance())
                .setAudience(Collections.singletonList(googleOAuthProperties.clientId()))
                .build();
    }

    @Override
    public GoogleUserInfo verify(String idToken) {
        GoogleIdToken googleIdToken;
        try {
            googleIdToken = verifier.verify(idToken);
        } catch (GeneralSecurityException | IOException | IllegalArgumentException e) {
            throw new BusinessException(BusinessErrorCode.INVALID_GOOGLE_TOKEN);
        }

        if (googleIdToken == null) {
            throw new BusinessException(BusinessErrorCode.INVALID_GOOGLE_TOKEN);
        }

        GoogleIdToken.Payload payload = googleIdToken.getPayload();
        return new GoogleUserInfo(
                payload.getSubject(),
                payload.getEmail(),
                Boolean.TRUE.equals(payload.getEmailVerified()),
                (String) payload.get("name")
        );
    }
}
