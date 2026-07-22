package com.fptis.intern.server.domain.user;

public enum Role {
    USER,           // 관광객 (일반 유저)
    BRANCH_ADMIN,   // 환전소 직원
    ADMIN,          // 시스템 관리자
    AI_AGENT        // AI 파트 (내부 시스템 연동)
}
