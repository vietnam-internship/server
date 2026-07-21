package com.fptis.intern.server.application.auth;

import com.fptis.intern.server.domain.auth.GoogleUserInfo;

/**
 * 인터페이스 뒤에 실제 검증(JWKS/aud/iss/exp/서명)을 숨긴다 — 테스트에서는 목으로 대체해
 * CI가 구글 네트워크를 타지 않게 한다.
 */
public interface GoogleTokenVerifier {

    GoogleUserInfo verify(String idToken);
}
