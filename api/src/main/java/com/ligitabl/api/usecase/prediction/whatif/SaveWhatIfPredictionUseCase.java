package com.ligitabl.api.usecase.prediction.whatif;

import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Component;

import com.ligitabl.model.domain.prediction.WhatIfPrediction;
import com.ligitabl.model.domain.prediction.WhatIfScore;
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
                .scores(scores == null ? List.of() : scores)
                .build());
    }
}
