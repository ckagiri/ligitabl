package com.ligitabl.api.usecases.prediction.seeding;

import com.ligitabl.model.domain.Contest;
import com.ligitabl.model.domain.Season;
import com.ligitabl.model.domain.SeasonPrediction;
import com.ligitabl.model.domain.User;

import java.util.List;
import java.util.Map;
import java.util.UUID;


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
