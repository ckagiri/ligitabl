package com.ligitabl.api.runners.demoseeding;

import java.util.List;

import com.ligitabl.model.domain.contest.Contest;
import com.ligitabl.model.domain.identity.User;
import com.ligitabl.model.domain.league.Competition;
import com.ligitabl.model.domain.league.Round;
import com.ligitabl.model.domain.league.Season;
import com.ligitabl.model.domain.league.Team;

public record ValidationContext(
        Competition competition,
        Season season,
        List<Round> rounds,
        List<Team> teams,
        Contest defaultContest,
        List<User> users) {}
