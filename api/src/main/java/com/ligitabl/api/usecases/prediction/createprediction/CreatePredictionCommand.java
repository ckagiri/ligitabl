package com.ligitabl.api.usecases.prediction.createprediction;

import java.util.List;

public record CreatePredictionCommand(List<TeamRankRequest> rankings) {
    public record TeamRankRequest(String code, int position) {}
}
