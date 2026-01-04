package com.ligitabl.api.usecases.contest;

import java.util.List;

public record JoinContestCommand(List<TeamRankRequest> rankings) {
    public record TeamRankRequest(String code, int position) {}
}
