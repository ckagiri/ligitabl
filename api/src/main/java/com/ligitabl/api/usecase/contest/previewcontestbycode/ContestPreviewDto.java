package com.ligitabl.api.usecase.contest.previewcontestbycode;

import java.util.UUID;

import lombok.Builder;

@Builder
public record ContestPreviewDto(
        UUID id,
        String name,
        String scopeCode,
        String scopeLabel,
        String gwRange,
        String dateRange,
        int memberCount,
        boolean isOpen) {}
