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
     * Returns a paginated slice of contests the user has joined for the given tab (active or past),
     * ordered general-first then private, each group by most recently joined.
     */
    List<UserContestView> findContestsByUserId(UUID userId, boolean completed, int limit, int offset);

    /** Total number of contests for the given tab (active or past). Used to drive the pager. */
    int countContestsByUserId(UUID userId, boolean completed);

    Optional<Contest> findByJoinCode(String joinCode);

    void delete(UUID contestId);
}
