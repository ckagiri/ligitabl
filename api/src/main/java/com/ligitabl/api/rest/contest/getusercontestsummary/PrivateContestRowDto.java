package com.ligitabl.api.rest.contest.getusercontestsummary;

import java.util.List;
import java.util.UUID;

public record PrivateContestRowDto(
        UUID id,
        String name,
        String dateLabel,
        String dateFrom,
        String dateTo,
        String gwLabel,
        int gwFrom,
        int gwTo,
        int memberCount,
        boolean isOwner,
        String status,
        boolean isJoiningOpen,
        boolean renewVisible,
        boolean renewEnabled,
        String renewFromCode,
        String renewDefaultToCode,
        List<String> renewToOptionCodes) {}
