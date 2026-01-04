package com.ligitabl.api.usecases.contest.joincontest;

import java.util.List;

public record JoinContestCommand(List<TeamRankRequest> rankings) {
    public record TeamRankRequest(String code, int position) {}
}
