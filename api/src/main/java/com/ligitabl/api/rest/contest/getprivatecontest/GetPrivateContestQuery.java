package com.ligitabl.api.rest.contest.getprivatecontest;

import java.util.UUID;

/**
 * @param selectedSegmentCode phase code chosen by the user; caller (controller) must resolve a
 *     default before constructing this query — never null
 * @param page zero-based page index for leaderboard pagination
 */
public record GetPrivateContestQuery(UUID contestId, UUID userId, String selectedSegmentCode, int page) {}
