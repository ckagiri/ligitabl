package com.ligitabl.api.rest.contest.shared;

import java.util.UUID;

import org.springframework.stereotype.Component;

import com.ligitabl.model.domain.LeaderboardEntry;
import com.ligitabl.model.repo.LeaderboardRepo;

import lombok.RequiredArgsConstructor;

/** Resolves a user's rank (and movement) within a contest's leaderboard for a given round range. */
@Component
@RequiredArgsConstructor
public class ContestRankResolver {

    private final LeaderboardRepo leaderboardRepo;

    public record RankInfo(Integer position, int movement) {
        public static final RankInfo NONE = new RankInfo(null, 0);
    }

    public RankInfo resolve(UUID contestId, UUID seasonId, int fromRound, int toRound, UUID userId) {
        var response = leaderboardRepo.computeLeaderboard(contestId, seasonId, fromRound, toRound, userId, 0, 1, true);
        LeaderboardEntry userEntry = response.userEntry();
        return userEntry != null
                ? new RankInfo(userEntry.scored() ? userEntry.position() : null, userEntry.movement())
                : RankInfo.NONE;
    }
}
