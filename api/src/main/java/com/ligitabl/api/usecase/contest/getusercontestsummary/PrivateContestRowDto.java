package com.ligitabl.api.usecase.contest.getusercontestsummary;

import java.util.UUID;

import com.ligitabl.api.usecase.contest.shared.RenewalOptions;

import lombok.Builder;

@Builder
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
        boolean isOpenForJoining,
        RenewalOptions renewal) {}
