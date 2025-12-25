package com.ligitabl.api.usecases.prediction.makeswap;

public record SwapCommand(
        String teamACode,
        String teamBCode
) {}
