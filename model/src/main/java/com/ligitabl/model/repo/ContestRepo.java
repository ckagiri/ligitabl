package com.ligitabl.model.repo;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.ligitabl.model.domain.Contest;

public interface ContestRepo {

    /** Projection for any contest a user has joined, used for the profile My Contests lists. */
    record UserContestView(
            UUID contestId,
            String contestName,
            UUID seasonId,
            String seasonName,
            boolean seasonCompleted,
            int fromRoundPosition,
            int toRoundPosition,
            boolean isPrivate) {}

    Optional<Contest> findById(UUID id);

    Contest save(Contest contest);

    Optional<Contest> findMainBySeasonId(UUID seasonId);

    boolean existsByUserAndContest(UUID userId, UUID contestId);

    List<Contest> findPrivateByUserId(UUID userId);

    /**
     * Returns all full-season general (non-private) contests the user has joined, most recently joined first.
     * Filters to from-round=1 and to-round=season.maxRounds (full-season scope only).
     */
    List<UserContestView> findGeneralContestsByUserId(UUID userId);

    /**
     * Returns all private contests the user has actively joined, most recently joined first.
     */
    List<UserContestView> findPrivateContestsByUserId(UUID userId);

    Optional<Contest> findByJoinCode(String joinCode);

    void delete(UUID contestId);
}
