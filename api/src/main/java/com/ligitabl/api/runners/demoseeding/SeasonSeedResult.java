package com.ligitabl.api.runners.demoseeding;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.ligitabl.model.domain.contest.Contest;
import com.ligitabl.model.domain.identity.User;
import com.ligitabl.model.domain.league.Season;
import com.ligitabl.model.domain.prediction.SeasonPrediction;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class SeasonSeedResult {
    private Season season;
    private List<User> users;
    private Contest defaultContest;
    private Map<UUID, SeasonPrediction> predictions;
    private int totalRounds;
    private int matchesSeeded;
    private int swapsSeeded;
    private int roundsFinalized;
    private List<String> warnings;
}
