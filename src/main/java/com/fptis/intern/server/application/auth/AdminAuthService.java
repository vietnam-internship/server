package com.fptis.intern.server.application.auth;

import com.fptis.intern.server.domain.user.Role;
import com.fptis.intern.server.domain.user.User;
import com.fptis.intern.server.domain.user.UserRepository;
import com.fptis.intern.server.global.exception.BusinessErrorCode;
import com.fptis.intern.server.global.exception.BusinessException;
import com.fptis.intern.server.global.util.JwtProvider;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminAuthService {

    private static final Set<Role> ADMIN_ROLES = Set.of(Role.ADMIN, Role.BRANCH_ADMIN);

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtProvider jwtProvider;
    private final RefreshTokenService refreshTokenService;

    @Transactional
    public LoginResult loginWithPassword(String email, String rawPassword) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new BusinessException(BusinessErrorCode.UNAUTHORIZED));

        if (!ADMIN_ROLES.contains(user.getRole()) || user.getPassword() == null
                || !passwordEncoder.matches(rawPassword, user.getPassword())) {
            throw new BusinessException(BusinessErrorCode.UNAUTHORIZED);
        }

        String accessToken = jwtProvider.createAccessToken(user.getId(), user.getRole());
        String refreshToken = refreshTokenService.issue(user.getId()).rawToken();
        return new LoginResult(accessToken, refreshToken, false, user);
    }
}
