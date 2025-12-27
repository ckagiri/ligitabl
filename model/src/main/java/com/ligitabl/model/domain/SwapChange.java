package com.ligitabl.model.domain;

import java.time.Instant;

public record SwapChange(
        Instant timestamp,
        String teamA, // Format: "ARS:1→4"
        String teamB // Format: "CHE:4→1"
        ) {}
