package com.ligitabl.model.repo;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import com.ligitabl.model.domain.Match;

public interface MatchRepo {

    List<Match> findByRoundId(UUID roundId);

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
}
