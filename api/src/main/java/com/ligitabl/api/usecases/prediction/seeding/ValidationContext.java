package com.ligitabl.api.usecases.prediction.seeding;

import com.ligitabl.model.domain.*;

import java.util.List;

public record ValidationContext(
        Competition competition,
        Season season,
        List<Round> rounds,
        List<Team> teams,
        Contest defaultContest
) {}
