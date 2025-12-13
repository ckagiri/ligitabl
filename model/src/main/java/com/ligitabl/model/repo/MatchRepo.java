package com.ligitabl.model.repo;

import java.util.List;
import java.util.UUID;

import com.ligitabl.model.domain.Match;

public interface MatchRepo {

    List<Match> findByRoundId(UUID roundId);
}
