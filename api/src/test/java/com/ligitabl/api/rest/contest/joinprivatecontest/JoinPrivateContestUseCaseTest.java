package com.ligitabl.api.rest.contest.joinprivatecontest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.ligitabl.api.rest.round.shared.RoundSupport;
import com.ligitabl.model.domain.*;
import com.ligitabl.model.repo.*;

@ExtendWith(MockitoExtension.class)
class JoinPrivateContestUseCaseTest {

    @Mock
    ContestRepo contestRepo;

    @Mock
    EntryRepo entryRepo;

    @Mock
    SeasonPredictionRepo predictionRepo;

    @Mock
    SeasonRepo seasonRepo;

    @Mock
    RoundRepo roundRepo;

    @Mock
    CompetitionRepo competitionRepo;

    @Mock
    RoundSupport roundSupport;

    private JoinPrivateContestUseCase useCase;

    private static final String CODE = "AB3K7PQ";

    private UUID userId;
    private UUID contestId;
    private UUID seasonId;
    private UUID competitionId;
    private UUID roundId;

    private Contest openContest;
    private Season season;
    private Round currentRound;
    private Competition competition;

    // Sprint S4 from=16 to=20 → contest window ends at round 20
    private List<RoundSpan> phases;

    @BeforeEach
    void setUp() {
        useCase = new JoinPrivateContestUseCase(
                contestRepo, entryRepo, predictionRepo, seasonRepo, roundRepo, competitionRepo, roundSupport);

        userId = UUID.randomUUID();
        contestId = UUID.randomUUID();
        seasonId = UUID.randomUUID();
        competitionId = UUID.randomUUID();
        roundId = UUID.randomUUID();

        phases = List.of(
                RoundSpan.builder()
                        .code("S3")
                        .name("Sprint 3")
                        .type(PhaseType.SPRINT)
                        .from(11)
                        .to(15)
                        .build(),
                RoundSpan.builder()
                        .code("S4")
                        .name("Sprint 4")
                        .type(PhaseType.SPRINT)
                        .from(16)
                        .to(20)
                        .build(),
                RoundSpan.builder()
                        .code("Q2")
                        .name("Quarter 2")
                        .type(PhaseType.QUARTER)
                        .from(11)
                        .to(20)
                        .build());

        openContest = Contest.builder()
                .id(contestId)
                .seasonId(seasonId)
                .name("OfficeMates")
                .isPrivate(true)
                .isOpen(true)
                .joinCode(CODE)
                .fromRoundPosition(11)
                .toRoundPosition(20)
                .ownerId(UUID.randomUUID())
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
                .position(11)
                .name("Round 11")
                .slug("round-11")
                .build();

        competition = Competition.builder()
                .id(competitionId)
                .name("Premier League")
                .slug(CompetitionSlug.of("premier-league"))
                .code("PL")
                .phases(phases)
                .build();
    }

    @Test
    void happyPath_createsEntryWithCurrentRound() {
        when(contestRepo.findByJoinCode(CODE)).thenReturn(Optional.of(openContest));
        when(seasonRepo.findById(seasonId)).thenReturn(Optional.of(season));
        when(predictionRepo.existsByUserAndSeason(userId, seasonId)).thenReturn(true);
        when(roundRepo.findById(roundId)).thenReturn(Optional.of(currentRound));
        when(competitionRepo.findById(competitionId)).thenReturn(Optional.of(competition));
        when(entryRepo.findByUserAndContest(userId, contestId)).thenReturn(Optional.empty());
        when(contestRepo.findPrivateByUserId(userId)).thenReturn(List.of());
        when(entryRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        var result = useCase.execute(new JoinPrivateContestCommand(userId, CODE));

        assertThat(result.isRight()).isTrue();

        ArgumentCaptor<Entry> captor = ArgumentCaptor.forClass(Entry.class);
        verify(entryRepo).save(captor.capture());
        assertThat(captor.getValue().getUserId()).isEqualTo(userId);
        assertThat(captor.getValue().getContestId()).isEqualTo(contestId);
        assertThat(captor.getValue().getJoinedAtRound()).isEqualTo(11);
    }

    @Test
    void codeNotFound_returnsContestNotFoundError() {
        when(contestRepo.findByJoinCode(CODE)).thenReturn(Optional.empty());

        var result = useCase.execute(new JoinPrivateContestCommand(userId, CODE));

        assertThat(result.isLeft()).isTrue();
        assertThat(result.getLeft()).isInstanceOf(JoinPrivateContestError.ContestNotFound.class);
    }

    @Test
    void contestClosed_returnsContestClosedError() {
        Contest closed = Contest.builder()
                .id(contestId)
                .seasonId(seasonId)
                .name("Closed")
                .isOpen(false)
                .joinCode(CODE)
                .build();
        when(contestRepo.findByJoinCode(CODE)).thenReturn(Optional.of(closed));

        var result = useCase.execute(new JoinPrivateContestCommand(userId, CODE));

        assertThat(result.isLeft()).isTrue();
        assertThat(result.getLeft()).isInstanceOf(JoinPrivateContestError.ContestClosed.class);
    }

    @Test
    void seasonOffSeason_returnsSeasonNotJoinableError() {
        Season offSeason = Season.builder()
                .id(seasonId)
                .competitionId(competitionId)
                .currentRoundId(roundId)
                .name("2024/25")
                .slug(SeasonSlug.of("2024-25"))
                .clientId(1)
                .maxRounds(20)
                .totalTeams(12)
                .completed(true)
                .endDate(java.time.LocalDate.now().minusDays(1))
                .build();
        when(contestRepo.findByJoinCode(CODE)).thenReturn(Optional.of(openContest));
        when(seasonRepo.findById(seasonId)).thenReturn(Optional.of(offSeason));

        var result = useCase.execute(new JoinPrivateContestCommand(userId, CODE));

        assertThat(result.isLeft()).isTrue();
        assertThat(result.getLeft()).isInstanceOf(JoinPrivateContestError.SeasonNotJoinable.class);
    }

    @Test
    void seasonPreSeason_stillJoinable() {
        Season preSeason = Season.builder()
                .id(seasonId)
                .competitionId(competitionId)
                .currentRoundId(roundId)
                .name("2026/27")
                .slug(SeasonSlug.of("2026-27"))
                .clientId(1)
                .maxRounds(20)
                .totalTeams(12)
                .completed(false)
                .startDate(java.time.LocalDate.now().plusDays(30))
                .preSeasonOpensAt(OffsetDateTime.now().minusDays(1))
                .predictionsOpenAt(OffsetDateTime.now().plusDays(29))
                .build();
        when(contestRepo.findByJoinCode(CODE)).thenReturn(Optional.of(openContest));
        when(seasonRepo.findById(seasonId)).thenReturn(Optional.of(preSeason));
        when(predictionRepo.existsByUserAndSeason(userId, seasonId)).thenReturn(true);
        when(roundRepo.findById(roundId)).thenReturn(Optional.of(currentRound));
        when(competitionRepo.findById(competitionId)).thenReturn(Optional.of(competition));
        when(entryRepo.findByUserAndContest(userId, contestId)).thenReturn(Optional.empty());
        when(contestRepo.findPrivateByUserId(userId)).thenReturn(List.of());
        when(entryRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        var result = useCase.execute(new JoinPrivateContestCommand(userId, CODE));

        assertThat(result.isRight()).isTrue();
    }

    @Test
    void userHasNoPrediction_returnsNoPredictionError() {
        when(contestRepo.findByJoinCode(CODE)).thenReturn(Optional.of(openContest));
        when(seasonRepo.findById(seasonId)).thenReturn(Optional.of(season));
        when(predictionRepo.existsByUserAndSeason(userId, seasonId)).thenReturn(false);

        var result = useCase.execute(new JoinPrivateContestCommand(userId, CODE));

        assertThat(result.isLeft()).isTrue();
        assertThat(result.getLeft()).isInstanceOf(JoinPrivateContestError.NoPrediction.class);
    }

    @Test
    void userAlreadyActiveMember_returnsAlreadyMemberError() {
        when(contestRepo.findByJoinCode(CODE)).thenReturn(Optional.of(openContest));
        when(seasonRepo.findById(seasonId)).thenReturn(Optional.of(season));
        when(predictionRepo.existsByUserAndSeason(userId, seasonId)).thenReturn(true);
        when(roundRepo.findById(roundId)).thenReturn(Optional.of(currentRound));
        when(competitionRepo.findById(competitionId)).thenReturn(Optional.of(competition));

        Entry activeEntry = Entry.builder()
                .userId(userId)
                .contestId(contestId)
                .joinedAtRound(11)
                .build();
        when(entryRepo.findByUserAndContest(userId, contestId)).thenReturn(Optional.of(activeEntry));

        var result = useCase.execute(new JoinPrivateContestCommand(userId, CODE));

        assertThat(result.isLeft()).isTrue();
        assertThat(result.getLeft()).isInstanceOf(JoinPrivateContestError.AlreadyMember.class);
    }

    @Test
    void memberCapReached_returnsCapReachedError() {
        Contest cappedContest = Contest.builder()
                .id(contestId)
                .seasonId(seasonId)
                .name("Capped")
                .isOpen(true)
                .joinCode(CODE)
                .fromRoundPosition(11)
                .toRoundPosition(20)
                .maxEntries(5)
                .ownerId(UUID.randomUUID())
                .build();
        when(contestRepo.findByJoinCode(CODE)).thenReturn(Optional.of(cappedContest));
        when(seasonRepo.findById(seasonId)).thenReturn(Optional.of(season));
        when(predictionRepo.existsByUserAndSeason(userId, seasonId)).thenReturn(true);
        when(roundRepo.findById(roundId)).thenReturn(Optional.of(currentRound));
        when(competitionRepo.findById(competitionId)).thenReturn(Optional.of(competition));
        when(entryRepo.findByUserAndContest(userId, contestId)).thenReturn(Optional.empty());
        when(entryRepo.countActiveByContestId(contestId)).thenReturn(5);

        var result = useCase.execute(new JoinPrivateContestCommand(userId, CODE));

        assertThat(result.isLeft()).isTrue();
        assertThat(result.getLeft()).isInstanceOf(JoinPrivateContestError.MemberCapReached.class);
    }

    @Test
    void reJoin_softRemovedEntry_entryUpserted() {
        when(contestRepo.findByJoinCode(CODE)).thenReturn(Optional.of(openContest));
        when(seasonRepo.findById(seasonId)).thenReturn(Optional.of(season));
        when(predictionRepo.existsByUserAndSeason(userId, seasonId)).thenReturn(true);
        when(roundRepo.findById(roundId)).thenReturn(Optional.of(currentRound));
        when(competitionRepo.findById(competitionId)).thenReturn(Optional.of(competition));

        Entry removedEntry = Entry.builder()
                .userId(userId)
                .contestId(contestId)
                .joinedAtRound(11)
                .removedAtRound(12)
                .build();
        when(entryRepo.findByUserAndContest(userId, contestId)).thenReturn(Optional.of(removedEntry));
        when(entryRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        var result = useCase.execute(new JoinPrivateContestCommand(userId, CODE));

        assertThat(result.isRight()).isTrue();
        // No cap/count check for re-joins; save() still called
        verify(entryRepo).save(any());
        verify(entryRepo, never()).countActiveByContestId(any());
    }

    @Test
    void joinWindowClosed_pastLastSprintStart_returnsError() {
        // Current round 17 (past opening of last sprint S4=16-20)
        Round lateRound = Round.builder()
                .id(roundId)
                .seasonId(seasonId)
                .position(17)
                .name("Round 17")
                .slug("round-17")
                .build();
        when(contestRepo.findByJoinCode(CODE)).thenReturn(Optional.of(openContest));
        when(seasonRepo.findById(seasonId)).thenReturn(Optional.of(season));
        when(predictionRepo.existsByUserAndSeason(userId, seasonId)).thenReturn(true);
        when(roundRepo.findById(roundId)).thenReturn(Optional.of(lateRound));
        when(competitionRepo.findById(competitionId)).thenReturn(Optional.of(competition));

        var result = useCase.execute(new JoinPrivateContestCommand(userId, CODE));

        assertThat(result.isLeft()).isTrue();
        assertThat(result.getLeft()).isInstanceOf(JoinPrivateContestError.JoinWindowClosed.class);
    }

    @Test
    void joinWindowOpen_atExactStartOfLastSprint_openRound_allowed() {
        // Current round 16 (exact start of last sprint S4) and round is OPEN
        Round openingRound = Round.builder()
                .id(roundId)
                .seasonId(seasonId)
                .position(16)
                .name("Round 16")
                .slug("round-16")
                .build();
        when(contestRepo.findByJoinCode(CODE)).thenReturn(Optional.of(openContest));
        when(seasonRepo.findById(seasonId)).thenReturn(Optional.of(season));
        when(predictionRepo.existsByUserAndSeason(userId, seasonId)).thenReturn(true);
        when(roundRepo.findById(roundId)).thenReturn(Optional.of(openingRound));
        when(competitionRepo.findById(competitionId)).thenReturn(Optional.of(competition));
        when(roundSupport.resolveStatus(openingRound)).thenReturn(RoundStatus.OPEN);
        when(entryRepo.findByUserAndContest(userId, contestId)).thenReturn(Optional.empty());
        when(contestRepo.findPrivateByUserId(userId)).thenReturn(List.of());
        when(entryRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        var result = useCase.execute(new JoinPrivateContestCommand(userId, CODE));

        assertThat(result.isRight()).isTrue();
    }
}
