package com.ligitabl.api.web.predictions.whatif;

import com.ligitabl.model.domain.league.Match;
import com.ligitabl.model.domain.league.Score;

import lombok.Builder;

@Builder
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
        return WhatIfMatchDto.builder()
                .matchId(match.getId().toString())
                .homeTeamCode(match.getHomeTeam().getCode())
                .homeTeamShortName(match.getHomeTeam().getShortName())
                .awayTeamCode(match.getAwayTeam().getCode())
                .awayTeamShortName(match.getAwayTeam().getShortName())
                .kickOff(match.getKickOff() != null ? match.getKickOff().toString() : null)
                .status(match.getStatus().name())
                .homeGoals(score != null ? score.getHomeGoals() : null)
                .awayGoals(score != null ? score.getAwayGoals() : null)
                .build();
    }
}
