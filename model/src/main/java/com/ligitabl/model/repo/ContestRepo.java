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
            boolean isPrivate,
            boolean isOpen) {}

    Optional<Contest> findById(UUID id);

    Contest save(Contest contest);

    Optional<Contest> findMainBySeasonId(UUID seasonId);

    boolean existsByUserAndContest(UUID userId, UUID contestId);

    List<Contest> findPrivateByUserId(UUID userId);

    List<Contest> findPrivateByUserId(UUID userId, UUID seasonId);

    /**
     * Returns a paginated slice of contests the user has joined for the given tab, ordered
     * general-first then private, each group by most recently joined. When {@code activeTab} is
     * true, only contests belonging to {@code activeSeasonId} are returned; otherwise contests
     * from any other season. {@code activeSeasonId} may be null if the competition has no active
     * season, in which case the active tab is empty and the past tab includes every season.
     */
    List<UserContestView> findContestsByUserId(
            UUID userId, UUID activeSeasonId, boolean activeTab, int limit, int offset);

    /** Total number of contests for the given tab. Used to drive the pager. */
    int countContestsByUserId(UUID userId, UUID activeSeasonId, boolean activeTab);

    Optional<Contest> findByJoinCode(String joinCode);

    /**
     * True if this owner already has another contest with this exact name, in this season, for
     * this exact round window. Scoped by window (not just season) so a renewal can reuse the
     * original's name unchanged — the renewed window never overlaps the original's. {@code
     * excludeContestId} lets a rename check against a contest's own current name without tripping
     * on itself; pass null when checking a brand new contest.
     */
    boolean existsByOwnerSeasonPeriodAndName(
            UUID seasonId, UUID ownerId, int fromRoundPosition, int toRoundPosition, String name, UUID excludeContestId);

    void delete(UUID contestId);
}
