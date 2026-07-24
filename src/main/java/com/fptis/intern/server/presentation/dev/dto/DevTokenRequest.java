package com.fptis.intern.server.presentation.dev.dto;

import com.fptis.intern.server.domain.user.Role;
import jakarta.validation.constraints.NotBlank;

public record DevTokenRequest(@NotBlank String email, Role role) {

    public Role roleOrDefault() {
        return role != null ? role : Role.USER;
    }
}
