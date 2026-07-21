package com.fptis.intern.server.application.auth;

import com.fptis.intern.server.global.config.GoogleOAuthProperties;
import com.fptis.intern.server.global.exception.BusinessErrorCode;
import com.fptis.intern.server.global.exception.BusinessException;
import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeTokenRequest;
import com.google.api.client.googleapis.auth.oauth2.GoogleTokenResponse;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import java.io.IOException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class GoogleAuthorizationCodeExchangerImpl implements GoogleAuthorizationCodeExchanger {

    private final GoogleOAuthProperties googleOAuthProperties;

    @Override
    public String exchangeForIdToken(String code) {
        try {
            GoogleTokenResponse tokenResponse = new GoogleAuthorizationCodeTokenRequest(
                    new NetHttpTransport(),
                    GsonFactory.getDefaultInstance(),
                    googleOAuthProperties.clientId(),
                    googleOAuthProperties.clientSecret(),
                    code,
                    googleOAuthProperties.redirectUri())
                    .execute();

            String idToken = tokenResponse.getIdToken();
            if (idToken == null) {
                throw new BusinessException(BusinessErrorCode.INVALID_GOOGLE_TOKEN);
            }
            return idToken;
        } catch (IOException e) {
            log.warn("[GoogleAuthorizationCodeExchanger] code exchange failed: {}", e.getMessage());
            throw new BusinessException(BusinessErrorCode.INVALID_GOOGLE_TOKEN);
        }
    }
}
