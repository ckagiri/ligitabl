package com.ligitabl.api.rest.contest.getusercontestsummary;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.ligitabl.api.rest.contest.shared.ContestRankResolver;
import com.ligitabl.api.web.contest.shared.ContestSupport;
import com.ligitabl.model.domain.*;
import com.ligitabl.model.repo.*;

@ExtendWith(MockitoExtension.class)
class GetUserContestSummaryUseCaseTest {

    @Mock
    CompetitionRepo competitionRepo;

    @Mock
    SeasonRepo seasonRepo;

    @Mock
    ContestRepo contestRepo;

    @Mock
    EntryRepo entryRepo;

    @Mock
    RoundRepo roundRepo;

    @Mock
    MatchRepo matchRepo;

    @Mock
    ContestRankResolver contestRankResolver;

    @Mock
    ContestSupport contestSupport;

    private GetUserContestSummaryUseCase useCase;

    private static final String SLUG = "premier-league";

    private UUID userId;
    private UUID competitionId;
    private UUID seasonId;
    private UUID roundId;

    private Competition competition;
    private Season season;
    private Round currentRound;

    @BeforeEach
    void setUp() {
        useCase = new GetUserContestSummaryUseCase(
                competitionRepo,
                seasonRepo,
                contestRepo,
                entryRepo,
                roundRepo,
                matchRepo,
                contestRankResolver,
                contestSupport);

        userId = UUID.randomUUID();
        competitionId = UUID.randomUUID();
        seasonId = UUID.randomUUID();
        roundId = UUID.randomUUID();

        competition = Competition.builder()
                .id(competitionId)
                .name("Premier League")
                .slug(CompetitionSlug.of(SLUG))
                .code("PL")
                .phases(List.of())
                .build();

        season = Season.builder()
                .id(seasonId)
                .competitionId(competitionId)
                .currentRoundId(roundId)
                .name("2025/26")
                .slug(SeasonSlug.of("2025-26"))
                .clientId(1)
                .maxRounds(20)
                .totalTeams(12)
                .build();

        currentRound = Round.builder()
                .id(roundId)
                .seasonId(seasonId)
                .position(20)
                .name("Round 20")
                .slug("round-20")
                .build();

        Contest mainContest = Contest.builder()
                .id(UUID.randomUUID())
                .seasonId(seasonId)
                .name("Main")
                .build();

        when(competitionRepo.findBySlug(SLUG)).thenReturn(Optional.of(competition));
        when(seasonRepo.findActiveSeason(competitionId)).thenReturn(Optional.of(season));
        when(contestRepo.findMainBySeasonId(seasonId)).thenReturn(Optional.of(mainContest));
        when(roundRepo.findById(roundId)).thenReturn(Optional.of(currentRound));
        when(matchRepo.groupRoundDateRangesBySeason(seasonId)).thenReturn(Map.of());
        when(entryRepo.countActiveByContestIds(any())).thenReturn(Map.of());
    }

    @Test
    void multiplePrivateContests_eachRowUsesResolvedCurrentRound() {
        Contest contestA = Contest.builder()
                .id(UUID.randomUUID())
                .seasonId(seasonId)
                .name("A")
                .isOpen(true)
                .fromRoundPosition(1)
                .toRoundPosition(10)
                .ownerId(userId)
                .build();
        Contest contestB = Contest.builder()
                .id(UUID.randomUUID())
                .seasonId(seasonId)
                .name("B")
                .isOpen(true)
                .fromRoundPosition(11)
                .toRoundPosition(20)
                .ownerId(userId)
                .build();
        when(contestRepo.findPrivateByUserId(userId, seasonId)).thenReturn(List.of(contestA, contestB));
        when(contestSupport.isOpenForJoining(eq(true), eq(10), eq(currentRound)))
                .thenReturn(false);
        when(contestSupport.isOpenForJoining(eq(true), eq(20), eq(currentRound)))
                .thenReturn(true);

        var result = useCase.execute(new GetUserContestSummaryQuery(userId, SLUG));

        assertThat(result.privateContests()).hasSize(2);
        assertThat(result.privateContests().get(0).isOpen()).isFalse();
        assertThat(result.privateContests().get(1).isOpen()).isTrue();

        // Current round is looked up once per list build, not once per row.
        verify(roundRepo, times(1)).findById(roundId);
    }
}
