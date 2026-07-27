package com.fptis.intern.server.application.user;

import com.fptis.intern.server.domain.auth.RefreshTokenRepository;
import com.fptis.intern.server.domain.user.User;
import com.fptis.intern.server.domain.user.UserRepository;
import com.fptis.intern.server.global.exception.BusinessErrorCode;
import com.fptis.intern.server.global.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;

    public User getMyProfile(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(BusinessErrorCode.UNAUTHORIZED));
    }

    @Transactional
    public void withdraw(Long userId) {
        refreshTokenRepository.deleteAllByUserId(userId);
        userRepository.deleteById(userId);
    }
}
