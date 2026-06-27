package com.ligitabl.api.rest.contest.getusercontestsummary;

public record GeneralContestRowDto(
        String phaseCode,
        String label,
        String dateLabel,
        String gwLabel,
        Integer rank,
        int movement) {}
