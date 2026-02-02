package com.ligitabl.api.rest.prediction.createprediction;

import java.util.List;

public record CreatePredictionCommand(List<TeamRankDto> rankings) {
}
