package com.ligitabl.api.rest.contest.getusercontestsummary;

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

    /**
     * Phases mirror a season's S1-S8 / Q1-Q4 structure, one round per sprint: S1=round1 ...
     * S8=round8, Q1=S1+S2, Q2=S3+S4, Q3=S5+S6, Q4=S7+S8.
     */
    private static List<RoundSpan> buildPhases() {
        List<RoundSpan> sprints = List.of(
                sprint("S1", 1, 1),
                sprint("S2", 2, 2),
                sprint("S3", 3, 3),
                sprint("S4", 4, 4),
                sprint("S5", 5, 5),
                sprint("S6", 6, 6),
                sprint("S7", 7, 7),
                sprint("S8", 8, 8));
        List<RoundSpan> quarters =
                List.of(quarter("Q1", 1, 2), quarter("Q2", 3, 4), quarter("Q3", 5, 6), quarter("Q4", 7, 8));
        return java.util.stream.Stream.concat(sprints.stream(), quarters.stream())
                .toList();
    }

    private static RoundSpan sprint(String code, int from, int to) {
        return RoundSpan.builder()
                .code(code)
                .name(code)
                .type(PhaseType.SPRINT)
                .from(from)
                .to(to)
                .build();
    }

    private static RoundSpan quarter(String code, int from, int to) {
        return RoundSpan.builder()
                .code(code)
                .name(code)
                .type(PhaseType.QUARTER)
                .from(from)
                .to(to)
                .build();
    }

    @Test
    void ownedRenewableContest_timingMet_renewalVisibleAndEnabled() {
        List<RoundSpan> phases = buildPhases();
        competition.setPhases(phases);
        currentRound.setPosition(8); // S8 — timing gate met for S6->S6
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
                .fromRoundPosition(6)
                .toRoundPosition(6)
                .ownerId(userId)
                .build();
        when(contestRepo.findPrivateByUserId(userId, seasonId)).thenReturn(List.of(contest));
        when(contestSupport.deriveContestStatus(6, 6, currentRound, phases)).thenReturn("FINISHED");

        var result = useCase.execute(new GetUserContestSummaryQuery(userId, SLUG));

        var row = result.privateContests().get(0);
        assertThat(row.renewVisible()).isTrue();
        assertThat(row.renewEnabled()).isTrue();
        assertThat(row.renewFromCode()).isEqualTo("S7");
        assertThat(row.renewDefaultToCode()).isEqualTo("S7");
        assertThat(row.renewToOptionCodes()).contains("S7", "S8");
    }

    @Test
    void ownedRenewableContest_timingNotMet_renewalVisibleButDisabled() {
        List<RoundSpan> phases = buildPhases();
        competition.setPhases(phases);
        currentRound.setPosition(6); // S6 — not yet 2 sprints past S6
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
                .fromRoundPosition(6)
                .toRoundPosition(6)
                .ownerId(userId)
                .build();
        when(contestRepo.findPrivateByUserId(userId, seasonId)).thenReturn(List.of(contest));
        when(contestSupport.deriveContestStatus(6, 6, currentRound, phases)).thenReturn("LIVE");

        var result = useCase.execute(new GetUserContestSummaryQuery(userId, SLUG));

        var row = result.privateContests().get(0);
        assertThat(row.renewVisible()).isTrue();
        assertThat(row.renewEnabled()).isFalse();
    }

    @Test
    void nonOwnedContest_renewalHidden() {
        List<RoundSpan> phases = buildPhases();
        competition.setPhases(phases);
        currentRound.setPosition(8);
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
                .fromRoundPosition(6)
                .toRoundPosition(6)
                .ownerId(UUID.randomUUID())
                .build();
        when(contestRepo.findPrivateByUserId(userId, seasonId)).thenReturn(List.of(contest));
        when(contestSupport.deriveContestStatus(6, 6, currentRound, phases)).thenReturn("FINISHED");

        var result = useCase.execute(new GetUserContestSummaryQuery(userId, SLUG));

        assertThat(result.privateContests().get(0).renewVisible()).isFalse();
    }

    @Test
    void alreadyRenewedContest_renewalHidden() {
        List<RoundSpan> phases = buildPhases();
        competition.setPhases(phases);
        currentRound.setPosition(8);
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
                .fromRoundPosition(6)
                .toRoundPosition(6)
                .ownerId(userId)
                .renewedIntoContestId(UUID.randomUUID())
                .build();
        when(contestRepo.findPrivateByUserId(userId, seasonId)).thenReturn(List.of(contest));
        when(contestSupport.deriveContestStatus(6, 6, currentRound, phases)).thenReturn("FINISHED");

        var result = useCase.execute(new GetUserContestSummaryQuery(userId, SLUG));

        assertThat(result.privateContests().get(0).renewVisible()).isFalse();
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
}
