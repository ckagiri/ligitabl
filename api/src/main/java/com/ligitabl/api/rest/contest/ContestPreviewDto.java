package com.ligitabl.api.rest.contest;

import java.util.UUID;

public record ContestPreviewDto(
        UUID id,
        String name,
        String scopeCode,
        String scopeLabel,
        String gwRange,
        int memberCount,
        boolean isOpen) {}
