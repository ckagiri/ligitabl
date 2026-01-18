package com.ligitabl.api.usecases.demoseeding;

import java.util.List;

import com.ligitabl.model.domain.*;

public record ValidationContext(
        Competition competition,
        Season season,
        List<Round> rounds,
        List<Team> teams,
        Contest defaultContest,
        List<User> users) {}
