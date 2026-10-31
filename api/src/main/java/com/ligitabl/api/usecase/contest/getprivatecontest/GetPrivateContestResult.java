package com.ligitabl.api.usecase.contest.getprivatecontest;

import java.util.List;

import com.ligitabl.api.usecase.contest.shared.SegmentNodeDto;
import com.ligitabl.model.domain.contest.Contest;
import com.ligitabl.model.domain.contest.Entry;
import com.ligitabl.model.domain.league.RoundSpan;
import com.ligitabl.model.query.LeaderboardResponse;

public record GetPrivateContestResult(
        Contest contest,
        RoundSpan selectedSegment,
        String currentSegmentCode,
        LeaderboardResponse leaderboard,
        boolean isOwner,
        List<Entry> members,
        String joinCode,
        List<SegmentNodeDto> segmentTree,
        String contestDateLabel,
        String contestPeriodLabel,
        boolean isOpenForJoining) {}
