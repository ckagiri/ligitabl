package com.ligitabl.api.web.predictions.whatif;

import java.util.List;

import com.ligitabl.model.domain.WhatIfScore;

public record WhatIfScoreDto(String matchId, int homeGoals, int awayGoals) {

    public static WhatIfScoreDto from(WhatIfScore score) {
        return new WhatIfScoreDto(score.matchId().toString(), score.homeGoals(), score.awayGoals());
    }

    public static List<WhatIfScoreDto> listOf(List<WhatIfScore> scores) {
        if (scores == null || scores.isEmpty()) {
            return List.of();
        }
        return scores.stream().map(WhatIfScoreDto::from).toList();
    }
}
