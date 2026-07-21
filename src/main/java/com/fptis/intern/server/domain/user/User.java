package com.fptis.intern.server.domain.user;

import com.fptis.intern.server.global.base.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * password/googleId 중 하나는 반드시 존재하지만, 가입 경로(비밀번호/구글)에 따라
 * 나머지 하나는 null일 수 있다 — DB unique 제약은 다중 NULL을 허용하므로 공존 가능(V1 참고).
 */
@Getter
@Entity
@Table(name = "users")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class User extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 50)
    private String name;

    @Column(unique = true)
    private String email;

    private String password;

    @Column(name = "google_id", unique = true)
    private String googleId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Role role;

    @Column(length = 20)
    private String phone;

    @Column(name = "phone_verified", nullable = false)
    private boolean phoneVerified;

    @Builder
    private User(String name, String email, String password, String googleId, Role role, String phone) {
        this.name = name;
        this.email = email;
        this.password = password;
        this.googleId = googleId;
        this.role = role;
        this.phone = phone;
        this.phoneVerified = false;
    }

    public void linkGoogleId(String googleId) {
        this.googleId = googleId;
    }
}
