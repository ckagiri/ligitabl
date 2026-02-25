package com.ligitabl.api.rest.prediction.createprediction;

import java.util.List;

public record CreatePredictionCommand(List<SwapPair> swaps) {
    public record SwapPair(String teamACode, String teamBCode) {}
}
