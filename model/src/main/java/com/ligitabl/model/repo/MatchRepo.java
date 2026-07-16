package com.ligitabl.model.repo;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import com.ligitabl.model.domain.Match;

public interface MatchRepo {

    /**
     * Min/max kickoff times for a single round position within a season.
     * Used to derive sprint start/end dates for display.
     */
    record RoundDateRange(int roundPosition, OffsetDateTime firstKickoff, OffsetDateTime lastKickoff) {}

    List<Match> findByRoundId(UUID roundId);

    /**
     * All matches of a season across all rounds, ordered by kickoff (nulls last), then id.
     */
    List<Match> findBySeasonId(UUID seasonId);

    Optional<Match> findByClientId(Integer clientId);

    /**
     * Match slugs are not globally unique; within a round they are.
     */
    Optional<Match> findByRoundIdAndSlug(UUID roundId, String slug);

    Match create(Match match);

    Match update(Match match);

    Match save(Match match);

    Optional<Match> findById(UUID id);

    /**
     * Finds match with teams loaded.
     */
    Optional<Match> findByIdWithTeams(UUID id);

    /**
     * Finds matches for round with teams loaded.
     */
    List<Match> findByRoundIdWithTeams(UUID roundId);

    /**
     * Finds finished matches up to round with teams loaded.
     */
    List<Match> findFinishedMatchesUpToRoundWithTeams(UUID seasonId, int roundPosition);

    boolean existsBySeasonAndRoundAndTeams(UUID seasonId, UUID roundId, UUID homeTeamId, UUID awayTeamId);

    Map<String, List<Match>> findBySeasonAndRound(UUID seasonId, int round);

    /**
     * Returns min/max kickoff times per round position for all rounds in a season.
     * Keyed by round position.
     */
    Map<Integer, RoundDateRange> groupRoundDateRangesBySeason(UUID seasonId);

    /**
     * True only if the round has at least one match and every match is FINISHED.
     * Used to guard season completion — the last round's matches must be genuinely finished,
     * not merely terminal-or-blocking (postponed/cancelled/suspended).
     */
    boolean allMatchesFinished(UUID roundId);
}
