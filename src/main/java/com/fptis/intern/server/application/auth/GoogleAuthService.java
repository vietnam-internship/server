package com.fptis.intern.server.application.auth;

import com.fptis.intern.server.domain.auth.GoogleUserInfo;
import com.fptis.intern.server.domain.auth.IssuedRefreshToken;
import com.fptis.intern.server.domain.user.Role;
import com.fptis.intern.server.domain.user.User;
import com.fptis.intern.server.domain.user.UserRepository;
import com.fptis.intern.server.global.exception.BusinessErrorCode;
import com.fptis.intern.server.global.exception.BusinessException;
import com.fptis.intern.server.global.util.JwtProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * find-or-create 우선순위: google_id 조회 → (없으면) email 조회 + email_verified 시 연동
 * → (없으면) 신규 생성. 미검증 이메일로는 절대 연동하지 않는다 — 계정 탈취 경로가 되기 때문.
 */
@Service
@RequiredArgsConstructor
public class GoogleAuthService {

    private final GoogleTokenVerifier googleTokenVerifier;
    private final UserRepository userRepository;
    private final JwtProvider jwtProvider;
    private final RefreshTokenService refreshTokenService;

    @Transactional
    public LoginResult loginWithGoogle(String idToken) {
        GoogleUserInfo googleUserInfo = googleTokenVerifier.verify(idToken);
        UserLookupResult lookup = findOrCreateUser(googleUserInfo);
        User user = lookup.user();

        String accessToken = jwtProvider.createAccessToken(user.getId(), user.getRole());
        IssuedRefreshToken refreshToken = refreshTokenService.issue(user.getId());

        return new LoginResult(accessToken, refreshToken.rawToken(), lookup.isNew(), user);
    }

    private UserLookupResult findOrCreateUser(GoogleUserInfo info) {
        return userRepository.findByGoogleId(info.googleId())
                .map(user -> new UserLookupResult(user, false))
                .orElseGet(() -> linkExistingOrCreate(info));
    }

    private UserLookupResult linkExistingOrCreate(GoogleUserInfo info) {
        if (info.emailVerified() && info.email() != null) {
            var existing = userRepository.findByEmail(info.email());
            if (existing.isPresent()) {
                User user = existing.get();
                user.linkGoogleId(info.googleId());
                return new UserLookupResult(user, false);
            }
        }

        try {
            User created = userRepository.save(User.builder()
                    .name(info.name() != null ? info.name() : info.email())
                    .email(info.email())
                    .googleId(info.googleId())
                    .role(Role.USER)
                    .build());
            return new UserLookupResult(created, true);
        } catch (DataIntegrityViolationException e) {
            // 동시 첫 로그인 경합 — unique 제약이 막았으니 다시 조회 (이 경로는 신규가 아니다)
            User user = userRepository.findByGoogleId(info.googleId())
                    .orElseThrow(() -> new BusinessException(BusinessErrorCode.INTERNAL_SERVER_ERROR));
            return new UserLookupResult(user, false);
        }
    }

    private record UserLookupResult(User user, boolean isNew) {
    }
}
