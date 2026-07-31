package com.ligitabl.api.rest.prediction.whatif;

import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Component;

import com.ligitabl.model.domain.WhatIfPrediction;
import com.ligitabl.model.repo.WhatIfPredictionRepo;

import lombok.RequiredArgsConstructor;

/**
 * Stores the scores a user last applied on the What-If page, one row per (user, round), so the
 * sandbox survives a page reload on another device. No business validation here —
 * {@link ComputeWhatIfUseCase} has already vetted the scores by the time this runs.
 */
@Component
@RequiredArgsConstructor
public class SaveWhatIfPredictionUseCase {

    private final WhatIfPredictionRepo whatIfPredictionRepo;

    public void execute(UUID userId, UUID roundId, List<WhatIfScore> scores) {
        whatIfPredictionRepo.save(WhatIfPrediction.builder()
                .userId(userId)
                .roundId(roundId)
                .scores(toDomain(scores))
                .build());
    }

    /** The model module can't see the api-module {@link WhatIfScore}, hence the same-shape mirror. */
    private static List<com.ligitabl.model.domain.WhatIfScore> toDomain(List<WhatIfScore> scores) {
        if (scores == null) {
            return List.of();
        }
        return scores.stream()
                .map(s -> new com.ligitabl.model.domain.WhatIfScore(s.matchId(), s.homeGoals(), s.awayGoals()))
                .toList();
    }
}
