package com.ligitabl.model.domain.prediction;

import java.util.UUID;

public record WhatIfScore(UUID matchId, int homeGoals, int awayGoals) {}
