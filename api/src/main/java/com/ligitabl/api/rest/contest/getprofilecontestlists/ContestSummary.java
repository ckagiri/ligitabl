package com.ligitabl.api.rest.contest.getprofilecontestlists;

import java.util.List;
import java.util.UUID;

public record ContestSummary(
        UUID contestId,
        String contestName,
        String seasonName,
        String periodLabel,
        int memberCount,
        Integer rank,
        String link,
        String status,
        boolean isPrivate,
        boolean isOwner,
        boolean isJoiningOpen,
        boolean renewVisible,
        boolean renewEnabled,
        String renewFromCode,
        String renewDefaultToCode,
        List<String> renewToOptionCodes) {}
