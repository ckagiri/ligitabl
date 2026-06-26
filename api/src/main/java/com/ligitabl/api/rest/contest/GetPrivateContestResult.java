package com.ligitabl.api.rest.contest;

import java.util.List;

import com.ligitabl.api.web.contest.SegmentNodeDto;
import com.ligitabl.model.domain.Contest;
import com.ligitabl.model.domain.Entry;
import com.ligitabl.model.domain.LeaderboardResponse;
import com.ligitabl.model.domain.RoundSpan;

public record GetPrivateContestResult(
        Contest contest,
        RoundSpan selectedSegment,
        LeaderboardResponse leaderboard,
        boolean isOwner,
        List<Entry> members,
        String joinCode,
        List<SegmentNodeDto> segmentTree) {}
