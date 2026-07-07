package com.ligitabl.api.rest.contest.getprofilecontestlists;

public record ContestSummary(
        String contestName,
        String seasonName,
        String periodLabel,
        int memberCount,
        Integer rank,
        String link,
        boolean isOpen) {}
