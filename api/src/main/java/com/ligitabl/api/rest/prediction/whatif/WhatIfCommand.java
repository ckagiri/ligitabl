package com.ligitabl.api.rest.prediction.whatif;

import java.util.List;

public record WhatIfCommand(List<WhatIfScore> scores) {
    public WhatIfCommand {
        scores = scores == null ? List.of() : List.copyOf(scores);
    }
}
