package com.ligitabl.model.repo;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.ligitabl.model.domain.Match;

public interface MatchRepo {

    List<Match> findByRoundId(UUID roundId);

    Optional<Match> findByClientId(Integer clientId);

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

    // Lightweight methods (no teams loaded)
    List<Match> findByRoundId(Long roundId);

    boolean existsBySeasonAndRoundAndTeams(
            UUID seasonId,
            UUID roundId,
            UUID homeTeamId,
            UUID awayTeamId
    );
}
