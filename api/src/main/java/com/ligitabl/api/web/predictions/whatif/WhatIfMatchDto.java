package com.ligitabl.api.web.predictions.whatif;

import com.ligitabl.model.domain.Match;

public record WhatIfMatchDto(
        String matchId,
        String homeTeamCode,
        String homeTeamShortName,
        String awayTeamCode,
        String awayTeamShortName,
        String kickOff,
        String status) {

    public static WhatIfMatchDto from(Match match) {
        return new WhatIfMatchDto(
                match.getId().toString(),
                match.getHomeTeam().getCode(),
                match.getHomeTeam().getShortName(),
                match.getAwayTeam().getCode(),
                match.getAwayTeam().getShortName(),
                match.getKickOff() != null ? match.getKickOff().toString() : null,
                match.getStatus().name());
    }
}
