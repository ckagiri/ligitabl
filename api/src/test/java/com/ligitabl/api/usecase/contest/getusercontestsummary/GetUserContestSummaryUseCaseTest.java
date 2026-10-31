package com.ligitabl.api.usecase.contest.getusercontestsummary;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
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

import com.ligitabl.api.usecase.contest.shared.ContestRankResolver;
import com.ligitabl.api.usecase.contest.shared.ContestSupport;
import com.ligitabl.model.domain.contest.Contest;
import com.ligitabl.model.domain.league.Competition;
import com.ligitabl.model.domain.league.CompetitionPhaseFixtures;
import com.ligitabl.model.domain.league.CompetitionSlug;
import com.ligitabl.model.domain.league.Round;
import com.ligitabl.model.domain.league.RoundSpan;
import com.ligitabl.model.domain.league.Season;
import com.ligitabl.model.domain.league.SeasonSlug;
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
    void ownedRenewableContest_timingMet_renewalVisibleAndEnabled() {
        List<RoundSpan> phases = CompetitionPhaseFixtures.phases();
        competition = competition.toBuilder().phases(phases).build();
        // still within S6, timing gate met, and before S7 (30-34) starts
        currentRound = currentRound.toBuilder().position(29).build();
        when(competitionRepo.findBySlug(SLUG)).thenReturn(Optional.of(competition));
        when(roundRepo.findById(roundId)).thenReturn(Optional.of(currentRound));
        when(contestRankResolver.resolve(any(), any(), anyInt(), anyInt(), any()))
                .thenReturn(ContestRankResolver.RankInfo.NONE);

        Contest contest = Contest.builder()
                .id(UUID.randomUUID())
                .seasonId(seasonId)
                .name("Office Rivals")
                .isPrivate(true)
                .isOpen(true)
                .fromRoundPosition(25)
                .toRoundPosition(29)
                .ownerId(userId)
                .build();
        when(contestRepo.findPrivateByUserId(userId, seasonId)).thenReturn(List.of(contest));
        when(contestSupport.deriveContestStatus(25, 29, currentRound, phases)).thenReturn("FINISHED");

        var result = useCase.execute(new GetUserContestSummaryQuery(userId, SLUG));

        var row = result.privateContests().get(0);
        assertThat(row.renewal().visible()).isTrue();
        assertThat(row.renewal().enabled()).isTrue();
        assertThat(row.renewal().fromCode()).isEqualTo("S7");
        assertThat(row.renewal().defaultToCode()).isEqualTo("S7");
        assertThat(row.renewal().toOptionCodes()).contains("S7", "S8");
    }

    @Test
    void ownedRenewableContest_renewalWindowElapsed_renewalHidden() {
        List<RoundSpan> phases = CompetitionPhaseFixtures.phases();
        competition = competition.toBuilder().phases(phases).build();
        // already in S8 — S7 (30-34), the renewal target, has fully elapsed
        currentRound = currentRound.toBuilder().position(35).build();
        when(competitionRepo.findBySlug(SLUG)).thenReturn(Optional.of(competition));
        when(roundRepo.findById(roundId)).thenReturn(Optional.of(currentRound));
        when(contestRankResolver.resolve(any(), any(), anyInt(), anyInt(), any()))
                .thenReturn(ContestRankResolver.RankInfo.NONE);

        Contest contest = Contest.builder()
                .id(UUID.randomUUID())
                .seasonId(seasonId)
                .name("Office Rivals")
                .isPrivate(true)
                .isOpen(true)
                .fromRoundPosition(25)
                .toRoundPosition(29)
                .ownerId(userId)
                .build();
        when(contestRepo.findPrivateByUserId(userId, seasonId)).thenReturn(List.of(contest));
        when(contestSupport.deriveContestStatus(25, 29, currentRound, phases)).thenReturn("FINISHED");

        var result = useCase.execute(new GetUserContestSummaryQuery(userId, SLUG));

        assertThat(result.privateContests().get(0).renewal().visible()).isFalse();
    }

    @Test
    void ownedRenewableContest_timingNotMet_renewalVisibleButDisabled() {
        List<RoundSpan> phases = CompetitionPhaseFixtures.phases();
        competition = competition.toBuilder().phases(phases).build();
        // within S6 (25-29), before the timing threshold (27)
        currentRound = currentRound.toBuilder().position(26).build();
        when(competitionRepo.findBySlug(SLUG)).thenReturn(Optional.of(competition));
        when(roundRepo.findById(roundId)).thenReturn(Optional.of(currentRound));
        when(contestRankResolver.resolve(any(), any(), anyInt(), anyInt(), any()))
                .thenReturn(ContestRankResolver.RankInfo.NONE);

        Contest contest = Contest.builder()
                .id(UUID.randomUUID())
                .seasonId(seasonId)
                .name("Office Rivals")
                .isPrivate(true)
                .isOpen(true)
                .fromRoundPosition(25)
                .toRoundPosition(29)
                .ownerId(userId)
                .build();
        when(contestRepo.findPrivateByUserId(userId, seasonId)).thenReturn(List.of(contest));
        when(contestSupport.deriveContestStatus(25, 29, currentRound, phases)).thenReturn("LIVE");

        var result = useCase.execute(new GetUserContestSummaryQuery(userId, SLUG));

        var row = result.privateContests().get(0);
        assertThat(row.renewal().visible()).isTrue();
        assertThat(row.renewal().enabled()).isFalse();
    }

    @Test
    void nonOwnedContest_renewalHidden() {
        List<RoundSpan> phases = CompetitionPhaseFixtures.phases();
        competition = competition.toBuilder().phases(phases).build();
        currentRound = currentRound.toBuilder().position(35).build();
        when(competitionRepo.findBySlug(SLUG)).thenReturn(Optional.of(competition));
        when(roundRepo.findById(roundId)).thenReturn(Optional.of(currentRound));
        when(contestRankResolver.resolve(any(), any(), anyInt(), anyInt(), any()))
                .thenReturn(ContestRankResolver.RankInfo.NONE);

        Contest contest = Contest.builder()
                .id(UUID.randomUUID())
                .seasonId(seasonId)
                .name("Office Rivals")
                .isPrivate(true)
                .isOpen(true)
                .fromRoundPosition(25)
                .toRoundPosition(29)
                .ownerId(UUID.randomUUID())
                .build();
        when(contestRepo.findPrivateByUserId(userId, seasonId)).thenReturn(List.of(contest));
        when(contestSupport.deriveContestStatus(25, 29, currentRound, phases)).thenReturn("FINISHED");

        var result = useCase.execute(new GetUserContestSummaryQuery(userId, SLUG));

        assertThat(result.privateContests().get(0).renewal().visible()).isFalse();
    }

    @Test
    void alreadyRenewedContest_renewalHidden() {
        List<RoundSpan> phases = CompetitionPhaseFixtures.phases();
        competition = competition.toBuilder().phases(phases).build();
        currentRound = currentRound.toBuilder().position(35).build();
        when(competitionRepo.findBySlug(SLUG)).thenReturn(Optional.of(competition));
        when(roundRepo.findById(roundId)).thenReturn(Optional.of(currentRound));
        when(contestRankResolver.resolve(any(), any(), anyInt(), anyInt(), any()))
                .thenReturn(ContestRankResolver.RankInfo.NONE);

        Contest contest = Contest.builder()
                .id(UUID.randomUUID())
                .seasonId(seasonId)
                .name("Office Rivals")
                .isPrivate(true)
                .isOpen(true)
                .fromRoundPosition(25)
                .toRoundPosition(29)
                .ownerId(userId)
                .renewedIntoContestId(UUID.randomUUID())
                .build();
        when(contestRepo.findPrivateByUserId(userId, seasonId)).thenReturn(List.of(contest));
        when(contestSupport.deriveContestStatus(25, 29, currentRound, phases)).thenReturn("FINISHED");

        var result = useCase.execute(new GetUserContestSummaryQuery(userId, SLUG));

        assertThat(result.privateContests().get(0).renewal().visible()).isFalse();
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
        when(contestSupport.deriveContestStatus(1, 10, currentRound, List.of())).thenReturn("FINISHED");
        when(contestSupport.deriveContestStatus(11, 20, currentRound, List.of()))
                .thenReturn("LIVE");

        var result = useCase.execute(new GetUserContestSummaryQuery(userId, SLUG));

        assertThat(result.privateContests()).hasSize(2);
        assertThat(result.privateContests().get(0).status()).isEqualTo("FINISHED");
        assertThat(result.privateContests().get(1).status()).isEqualTo("LIVE");

        // Current round is looked up once per list build, not once per row.
        verify(roundRepo, times(1)).findById(roundId);
    }

    @Test
    void buildGeneralRows_currentRoundFinalized_currentSprintUsesRawPosition() {
        List<RoundSpan> phases = CompetitionPhaseFixtures.phases();
        competition = competition.toBuilder().phases(phases).build();
        // inside S3 (10-14)
        currentRound = currentRound.toBuilder().position(12).finalized(true).build();
        when(competitionRepo.findBySlug(SLUG)).thenReturn(Optional.of(competition));
        when(roundRepo.findById(roundId)).thenReturn(Optional.of(currentRound));
        when(contestRepo.findPrivateByUserId(userId, seasonId)).thenReturn(List.of());
        when(contestRankResolver.resolve(any(), any(), anyInt(), anyInt(), any()))
                .thenReturn(ContestRankResolver.RankInfo.NONE);

        var result = useCase.execute(new GetUserContestSummaryQuery(userId, SLUG));

        assertThat(result.generalContests())
                .extracting(GeneralContestRowDto::phaseCode)
                .contains("S3");
    }

    @Test
    void buildGeneralRows_currentRoundNotFinalized_currentSprintStepsBackOnePosition() {
        List<RoundSpan> phases = CompetitionPhaseFixtures.phases();
        competition = competition.toBuilder().phases(phases).build();
        // opening round of S3 (10-14), not yet finalized
        currentRound = currentRound.toBuilder().position(10).finalized(false).build();
        when(competitionRepo.findBySlug(SLUG)).thenReturn(Optional.of(competition));
        when(roundRepo.findById(roundId)).thenReturn(Optional.of(currentRound));
        when(contestRepo.findPrivateByUserId(userId, seasonId)).thenReturn(List.of());
        when(contestRankResolver.resolve(any(), any(), anyInt(), anyInt(), any()))
                .thenReturn(ContestRankResolver.RankInfo.NONE);

        var result = useCase.execute(new GetUserContestSummaryQuery(userId, SLUG));

        // Effective position steps back to 9 (still inside S2, 5-9), not the raw position's S3.
        assertThat(result.generalContests())
                .extracting(GeneralContestRowDto::phaseCode)
                .contains("S2")
                .doesNotContain("S3");
    }
}
