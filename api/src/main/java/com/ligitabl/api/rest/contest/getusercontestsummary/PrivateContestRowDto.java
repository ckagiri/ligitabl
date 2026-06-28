package com.ligitabl.api.rest.contest.getusercontestsummary;

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
        boolean isOwner) {}
