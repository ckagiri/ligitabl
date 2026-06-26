package com.ligitabl.api.web.contest;

import java.util.List;

public record SegmentNodeDto(
        String id,
        String label,
        String gwLabel,
        String dateLabel,
        String status,
        Integer rank,
        boolean isTopTen,
        List<SegmentNodeDto> children) {}
