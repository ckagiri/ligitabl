package com.ligitabl.api.usecase.contest.shared;

import java.util.List;

import lombok.Builder;

@Builder
public record SegmentNodeDto(
        String id,
        String label,
        String gwLabel,
        String dateLabel,
        String status,
        String nodeType,
        Integer rank,
        boolean isTopTen,
        List<SegmentNodeDto> children) {}
