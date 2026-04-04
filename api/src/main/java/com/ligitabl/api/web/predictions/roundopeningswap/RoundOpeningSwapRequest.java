package com.ligitabl.api.web.predictions.roundopeningswap;

import java.util.List;

public record RoundOpeningSwapRequest(List<SwapEntry> swaps) {
    public record SwapEntry(String teamACode, String teamBCode) {}
}
