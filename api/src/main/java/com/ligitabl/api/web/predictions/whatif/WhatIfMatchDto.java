package com.ligitabl.api.web.predictions.whatif;

import com.ligitabl.model.domain.Match;
import com.ligitabl.model.domain.Score;

public record WhatIfMatchDto(
        String matchId,
        String homeTeamCode,
        String homeTeamShortName,
        String awayTeamCode,
        String awayTeamShortName,
        String kickOff,
        String status,
        Integer homeGoals,
        Integer awayGoals) {

    public static WhatIfMatchDto from(Match match) {
        Score score = match.getScore();
        return new WhatIfMatchDto(
                match.getId().toString(),
                match.getHomeTeam().getCode(),
                match.getHomeTeam().getShortName(),
                match.getAwayTeam().getCode(),
                match.getAwayTeam().getShortName(),
                match.getKickOff() != null ? match.getKickOff().toString() : null,
                match.getStatus().name(),
                score != null ? score.getHomeGoals() : null,
                score != null ? score.getAwayGoals() : null);
    }
}
