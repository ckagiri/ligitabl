package com.ligitabl.model.repo;

import java.util.Optional;
import java.util.UUID;

import com.ligitabl.model.domain.WhatIfPrediction;

public interface WhatIfPredictionRepo {
    WhatIfPrediction save(WhatIfPrediction prediction);

    Optional<WhatIfPrediction> findByUserAndRound(UUID userId, UUID roundId);

    void deleteByUserId(UUID userId);
}
