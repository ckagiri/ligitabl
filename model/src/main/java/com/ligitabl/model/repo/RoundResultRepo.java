package com.ligitabl.model.repo;


import com.ligitabl.model.domain.RoundResult;
import com.ligitabl.model.domain.Season;
import com.ligitabl.model.domain.User;

import java.util.List;
import java.util.Optional;

public interface RoundResultRepo {
    RoundResult save(RoundResult result);

    Optional<RoundResult> findByRoundSubmissionId(Long submissionId);

    Optional<RoundResult> findByUserAndSeasonAndRoundPosition(
            User user,
            Season season,
            int roundPosition
    );

    Optional<RoundResult> findByUserAndSeasonAndRoundPositionAndUserViewedFalse(
            User user,
            Season season,
            int roundPosition
    );

    List<RoundResult> findBySeasonAndRoundPositionRange(
            Long seasonId,
            int fromRound,
            int toRound
    );

    List<RoundResult> findByUserIdInAndSeasonIdAndRoundPositionBetween(
            List<Long> userIds,
            Long seasonId,
            int fromRound,
            int toRound
    );
}
