package com.ligitabl.model.query;

import java.util.UUID;

/** Projection for any contest a user has joined, used for the profile My Contests lists. */
public record UserContestView(
        UUID contestId,
        String contestName,
        UUID seasonId,
        String seasonName,
        boolean seasonCompleted,
        int fromRoundPosition,
        int toRoundPosition,
        boolean isPrivate,
        boolean isOpen,
        boolean isOwner) {}
