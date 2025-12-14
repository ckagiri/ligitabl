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
}
