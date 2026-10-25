package com.ligitabl.api.usecase.contest.getprivatecontest;

import java.util.List;

import com.ligitabl.api.usecase.contest.shared.SegmentNodeDto;
import com.ligitabl.model.domain.Contest;
import com.ligitabl.model.domain.Entry;
import com.ligitabl.model.domain.LeaderboardResponse;
import com.ligitabl.model.domain.RoundSpan;

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
