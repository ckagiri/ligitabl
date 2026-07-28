package com.ligitabl.api.rest.prediction.whatif;

import java.util.UUID;

public record WhatIfScore(UUID matchId, int homeGoals, int awayGoals) {}
