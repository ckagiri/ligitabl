package com.ligitabl.api.usecases.contest.getconteststatus;

import java.util.List;

import com.ligitabl.model.domain.Entry;
import com.ligitabl.model.domain.Season;
import com.ligitabl.model.domain.SeasonPrediction;

public record ContestStatus(
        boolean hasJoined, Season currentSeason, SeasonPrediction prediction, List<Entry> entries) {}
