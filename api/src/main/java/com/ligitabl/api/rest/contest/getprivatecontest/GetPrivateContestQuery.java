package com.ligitabl.api.rest.contest.getprivatecontest;

import java.util.UUID;

/**
 * @param selectedSegmentCode phase code chosen by the user; caller (controller) must resolve a
 *     default before constructing this query — never null
 */
public record GetPrivateContestQuery(UUID contestId, UUID userId, String selectedSegmentCode) {}
