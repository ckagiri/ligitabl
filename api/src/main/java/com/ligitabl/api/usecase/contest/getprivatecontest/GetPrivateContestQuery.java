package com.ligitabl.api.usecase.contest.getprivatecontest;

import java.util.UUID;

/**
 * @param selectedSegmentCode phase code chosen by the user; null means "the preferred level's current
 *     segment, else the current sprint"
 * @param page zero-based page index for leaderboard pagination
 * @param preferredLevel the level last viewed in this contest (from a cookie); ignored when
 *     {@code selectedSegmentCode} is set
 */
public record GetPrivateContestQuery(
        UUID contestId, UUID userId, String selectedSegmentCode, int page, SegmentLevel preferredLevel) {
    public GetPrivateContestQuery(UUID contestId, UUID userId, String selectedSegmentCode, int page) {
        this(contestId, userId, selectedSegmentCode, page, null);
    }
}
