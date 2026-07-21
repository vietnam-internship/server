package com.fptis.intern.server.application.auth;

import com.fptis.intern.server.domain.auth.IssuedRefreshToken;
import com.fptis.intern.server.domain.auth.RefreshToken;
import com.fptis.intern.server.domain.auth.RefreshTokenRepository;
import com.fptis.intern.server.global.exception.BusinessErrorCode;
import com.fptis.intern.server.global.exception.BusinessException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.HexFormat;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * refresh token은 JWT가 아니라 불투명 랜덤 값이다 — DB에는 SHA-256 해시만 저장한다.
 * 원문은 발급 순간 {@link IssuedRefreshToken}으로만 반환되고 서버 어디에도 남지 않으므로,
 * DB가 유출돼도 세션을 재구성할 수 없고, logout은 행 삭제만으로 즉시 폐기가 보장된다.
 */
@Service
@RequiredArgsConstructor
public class RefreshTokenService {

    public static final long REFRESH_TOKEN_EXPIRE_TIME = 14 * 24 * 60 * 60 * 1000L;

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final RefreshTokenRepository refreshTokenRepository;

    @Transactional
    public IssuedRefreshToken issue(Long userId) {
        String rawToken = generateRawToken();

        RefreshToken refreshToken = RefreshToken.builder()
                .userId(userId)
                .tokenHash(hash(rawToken))
                .expiresAt(LocalDateTime.now().plus(Duration.ofMillis(REFRESH_TOKEN_EXPIRE_TIME)))
                .build();
        refreshTokenRepository.save(refreshToken);

        return new IssuedRefreshToken(userId, rawToken);
    }

    /**
     * 제시된 refresh token을 폐기하고 같은 유저에게 새 토큰을 발급한다(회전).
     * 만료됐거나 존재하지 않으면 UNAUTHORIZED — 재사용 감지(전 세션 폐기)는 stretch로 미룬다.
     */
    @Transactional
    public IssuedRefreshToken rotate(String presentedRawToken) {
        RefreshToken current = refreshTokenRepository.findByTokenHash(hash(presentedRawToken))
                .orElseThrow(() -> new BusinessException(BusinessErrorCode.UNAUTHORIZED));

        refreshTokenRepository.delete(current);

        if (current.isExpired()) {
            throw new BusinessException(BusinessErrorCode.UNAUTHORIZED);
        }

        return issue(current.getUserId());
    }

    @Transactional
    public void revoke(String rawToken) {
        if (rawToken == null) {
            return;
        }
        refreshTokenRepository.deleteByTokenHash(hash(rawToken));
    }

    private String generateRawToken() {
        byte[] bytes = new byte[32];
        SECURE_RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String hash(String rawToken) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashBytes = digest.digest(rawToken.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hashBytes);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 알고리즘을 사용할 수 없습니다.", e);
        }
    }
}
