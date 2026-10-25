package com.ligitabl.api.usecase.prediction.roundopeningswap;

import java.util.List;

import com.ligitabl.api.usecase.prediction.makeswap.SwapCommand;

public record RoundOpeningSwapCommand(List<SwapCommand> swaps) {}
