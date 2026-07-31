package com.ligitabl.api.web.predictions.whatif;

import java.util.List;

public record WhatIfComputeRequest(List<WhatIfScoreInput> scores) {
    public WhatIfComputeRequest {
        scores = scores == null ? List.of() : List.copyOf(scores);
    }
}
