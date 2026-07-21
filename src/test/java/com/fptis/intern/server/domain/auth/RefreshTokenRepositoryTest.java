package com.fptis.intern.server.domain.auth;

import static org.assertj.core.api.Assertions.assertThat;

import com.fptis.intern.server.domain.user.Role;
import com.fptis.intern.server.domain.user.User;
import com.fptis.intern.server.domain.user.UserRepository;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * @DataJpaTest는 JPA 슬라이스만 띄우므로(웹/springdoc 제외) 실제 MySQL 컨테이너로
 * Flyway V1+V2 마이그레이션이 엔티티와 어긋나지 않는지(ddl-auto=validate)를 검증하는 용도로 쓴다.
 * 슬라이스 테스트는 전역 @Configuration(JpaAuditingConfig)을 스캔하지 않으므로
 * created_at/updated_at 채우기에 필요한 @EnableJpaAuditing을 여기서 다시 선언한다.
 */
@Testcontainers
@DataJpaTest
@EnableJpaAuditing
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class RefreshTokenRepositoryTest {

    @Container
    @ServiceConnection
    static MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.0");

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    @Test
    void savesAndFindsByTokenHash() {
        User user = userRepository.save(User.builder()
                .name("tester")
                .email("tester@example.com")
                .role(Role.USER)
                .build());

        refreshTokenRepository.save(RefreshToken.builder()
                .userId(user.getId())
                .tokenHash("hash-value")
                .expiresAt(LocalDateTime.now().plusDays(14))
                .build());

        assertThat(refreshTokenRepository.findByTokenHash("hash-value"))
                .isPresent()
                .get()
                .extracting(RefreshToken::getUserId)
                .isEqualTo(user.getId());

        refreshTokenRepository.deleteByTokenHash("hash-value");
        assertThat(refreshTokenRepository.findByTokenHash("hash-value")).isEmpty();
    }
}
