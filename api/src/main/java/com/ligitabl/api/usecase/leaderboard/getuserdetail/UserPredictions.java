package com.ligitabl.api.usecase.leaderboard.getuserdetail;

import java.util.List;

public record UserPredictions(int round, Integer roundScore, List<PredictionTeam> predictions, String seasonSlug) {}
