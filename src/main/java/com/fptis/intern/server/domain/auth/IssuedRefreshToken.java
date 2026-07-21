package com.fptis.intern.server.domain.auth;

/**
 * rawToken은 발급 순간에만 존재한다 — DB에는 해시만 저장되므로 이 응답 밖에서는 복원할 수 없다.
 */
public record IssuedRefreshToken(Long userId, String rawToken) {
}
