package com.ligitabl.api.usecase.prediction.whatif;

import java.util.List;

import com.ligitabl.model.domain.prediction.WhatIfScore;

public record WhatIfCommand(List<WhatIfScore> scores) {
    public WhatIfCommand {
        scores = scores == null ? List.of() : List.copyOf(scores);
    }
}
