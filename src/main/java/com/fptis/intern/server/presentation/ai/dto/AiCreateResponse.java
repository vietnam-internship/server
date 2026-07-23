package com.fptis.intern.server.presentation.ai.dto;

public record AiCreateResponse(
        Long id
) {
    public static AiCreateResponse from(Long id) {
        return new AiCreateResponse(id);
    }
}
