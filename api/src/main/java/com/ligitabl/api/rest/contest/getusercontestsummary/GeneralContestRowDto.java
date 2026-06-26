package com.ligitabl.api.rest.contest.getusercontestsummary;

public record GeneralContestRowDto(
        String phaseCode,
        String label,
        String gwLabel,
        Integer rank,
        int movement) {}
