package com.ligitabl.api.rest.contest.getprivatecontest;

import java.util.UUID;

/**
 * @param selectedSegmentCode phase code chosen by the user; null means "auto-select current sprint"
 * @param page zero-based page index for leaderboard pagination
 */
public record GetPrivateContestQuery(UUID contestId, UUID userId, String selectedSegmentCode, int page) {}
