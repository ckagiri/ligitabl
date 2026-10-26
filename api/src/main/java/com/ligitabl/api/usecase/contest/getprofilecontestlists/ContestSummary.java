package com.ligitabl.api.usecase.contest.getprofilecontestlists;

import java.util.UUID;

import com.ligitabl.api.usecase.contest.shared.RenewalOptions;

import lombok.Builder;

@Builder
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
        RenewalOptions renewal) {}
