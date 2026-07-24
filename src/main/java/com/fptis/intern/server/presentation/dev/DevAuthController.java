package com.fptis.intern.server.presentation.dev;

import com.fptis.intern.server.domain.user.User;
import com.fptis.intern.server.domain.user.UserRepository;
import com.fptis.intern.server.global.annotation.PublicApi;
import com.fptis.intern.server.global.util.JwtProvider;
import com.fptis.intern.server.presentation.dev.dto.DevTokenRequest;
import com.fptis.intern.server.presentation.dev.dto.DevTokenResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Google OAuth / phone 인증을 거치지 않고 access token을 바로 발급받기 위한 로컬 전용 엔드포인트.
 * travelx.dev.auth.enabled=true(DEV_AUTH_ENABLED)일 때만 빈 자체가 등록되므로,
 * 이 값을 세팅하지 않는 한(prod/staging 기본값 false) 컨트롤러가 아예 존재하지 않는다.
 */
@Slf4j
@RestController
@RequestMapping("/dev/auth")
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "travelx.dev.auth", name = "enabled", havingValue = "true")
public class DevAuthController {

    private final UserRepository userRepository;
    private final JwtProvider jwtProvider;

    /**
     * email로 유저를 찾아 없으면 새로 만든다(role 지정 가능, phone 인증도 자동 완료 처리) —
     * 예약 생성 등 phoneVerified가 필요한 플로우까지 별도 SQL 없이 바로 테스트할 수 있게 한다.
     */
    @PublicApi
    @PostMapping("/token")
    public DevTokenResponse issueToken(@Valid @RequestBody DevTokenRequest request) {
        User user = userRepository.findByEmail(request.email())
                .orElseGet(() -> createDevUser(request));

        String accessToken = jwtProvider.createAccessToken(user.getId(), user.getRole());
        log.warn("[DevAuthController] issued dev token for email={}, role={}", user.getEmail(), user.getRole());

        return DevTokenResponse.of(accessToken, jwtProvider.getAccessTokenExpireTime(), user);
    }

    private User createDevUser(DevTokenRequest request) {
        String namePrefix = request.email().split("@")[0];
        User user = User.builder()
                .name(namePrefix)
                .email(request.email())
                .role(request.roleOrDefault())
                .build();
        user.markPhoneVerified();
        return userRepository.save(user);
    }
}
