package com.fptis.intern.server.presentation.auth.dto;

import com.fptis.intern.server.domain.user.User;

public record UserSummaryResponse(Long id, String name, String email, String phone, boolean phoneVerified,
                                   String role) {

    public static UserSummaryResponse from(User user) {
        return new UserSummaryResponse(
                user.getId(),
                user.getName(),
                user.getEmail(),
                user.getPhone(),
                user.isPhoneVerified(),
                user.getRole().name());
    }
}
