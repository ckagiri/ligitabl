package com.ligitabl.api.rest.contest.shared;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.ligitabl.model.domain.LeaderboardEntry;
import com.ligitabl.model.domain.LeaderboardResponse;
import com.ligitabl.model.repo.LeaderboardRepo;

@ExtendWith(MockitoExtension.class)
class ContestRankResolverTest {

    @Mock
    LeaderboardRepo leaderboardRepo;

    private ContestRankResolver resolver;

    private final UUID contestId = UUID.randomUUID();
    private final UUID seasonId = UUID.randomUUID();
    private final UUID userId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        resolver = new ContestRankResolver(leaderboardRepo);
    }

    private void stubUserEntry(LeaderboardEntry entry) {
        when(leaderboardRepo.computeLeaderboard(
                        any(), any(), anyInt(), anyInt(), any(), anyInt(), anyInt(), anyBoolean()))
                .thenReturn(new LeaderboardResponse(List.of(), entry, false, 0, 1, false, false, null));
    }

    private LeaderboardEntry entry(int position, int movement, boolean scored) {
        return new LeaderboardEntry(position, "abc123", "User", 0, 0, 0, 0, 0, 1, movement, scored, false);
    }

    @Test
    void scoredEntryExposesPosition() {
        stubUserEntry(entry(3, 2, true));

        var rankInfo = resolver.resolve(contestId, seasonId, 1, 38, userId);

        assertThat(rankInfo.position()).isEqualTo(3);
        assertThat(rankInfo.movement()).isEqualTo(2);
    }

    @Test
    void unscoredEntryHasNoRank() {
        stubUserEntry(entry(1, 0, false));

        var rankInfo = resolver.resolve(contestId, seasonId, 1, 38, userId);

        assertThat(rankInfo.position()).isNull();
    }

    @Test
    void missingEntryYieldsNone() {
        stubUserEntry(null);

        var rankInfo = resolver.resolve(contestId, seasonId, 1, 38, userId);

        assertThat(rankInfo).isEqualTo(ContestRankResolver.RankInfo.NONE);
    }
}
