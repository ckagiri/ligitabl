package com.ligitabl.api.rest.contest.getusercontestsummary;

import java.util.UUID;

public record PrivateContestRowDto(
        UUID id, String name, String gwLabel, int memberCount, boolean isOwner) {}
