package com.fptis.intern.server.application.auth;

/**
 * 프론트가 /auth/callback에서 받은 authorization code를 구글 토큰 엔드포인트에서
 * id_token으로 교환한다 — client_secret이 필요한 confidential client 동작이라
 * 이 인터페이스 뒤에 격리해 테스트에서는 목으로 대체한다.
 */
public interface GoogleAuthorizationCodeExchanger {

    String exchangeForIdToken(String code);
}
