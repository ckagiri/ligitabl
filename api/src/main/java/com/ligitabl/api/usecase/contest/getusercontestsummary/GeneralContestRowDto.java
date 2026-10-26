package com.ligitabl.api.usecase.contest.getusercontestsummary;

import lombok.Builder;

@Builder
public record GeneralContestRowDto(
        String phaseCode,
        String label,
        String dateLabel,
        String dateFrom,
        String dateTo,
        String gwLabel,
        int gwFrom,
        int gwTo,
        Integer rank,
        int movement) {}
