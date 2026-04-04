package com.ligitabl.api.rest.prediction.roundopeningswap;

import java.util.List;

import com.ligitabl.api.rest.prediction.makeswap.SwapCommand;

public record RoundOpeningSwapCommand(List<SwapCommand> swaps) {}
