package com.ligitabl.api.usecases.seasonprediction.createseasonpred;

import java.util.List;

public record CreateSeasonPredictionCommand(List<TeamRankRequest> rankings) {
    public record TeamRankRequest(String code, int position) {}
}
