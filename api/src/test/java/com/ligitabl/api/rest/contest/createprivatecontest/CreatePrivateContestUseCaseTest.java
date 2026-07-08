package com.ligitabl.api.rest.contest.createprivatecontest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.ligitabl.api.contest.ContestCodeGenerator;
import com.ligitabl.api.rest.round.shared.RoundSupport;
import com.ligitabl.model.domain.*;
import com.ligitabl.model.repo.*;

@ExtendWith(MockitoExtension.class)
class CreatePrivateContestUseCaseTest {

    @Mock
    CompetitionRepo competitionRepo;

    @Mock
    SeasonRepo seasonRepo;

    @Mock
    RoundRepo roundRepo;

    @Mock
    RoundSupport roundSupport;

    @Mock
    ContestRepo contestRepo;

    @Mock
    EntryRepo entryRepo;

    @Mock
    ContestCodeGenerator codeGenerator;

    private CreatePrivateContestUseCase useCase;

    private static final String SLUG = "premier-league";
    private static final String CODE = "AB3K7PQ";

    private UUID userId;
    private UUID competitionId;
    private UUID seasonId;
    private UUID roundId;

    private Competition competition;
    private Season season;
    private Round currentRound;

    // Season phases: Q1 = S1(1-5)+S2(6-10), Q2 = S3(11-15)+S4(16-20)
    private List<RoundSpan> phases;

    @BeforeEach
    void setUp() {
        useCase = new CreatePrivateContestUseCase(
                competitionRepo, seasonRepo, roundRepo, roundSupport, contestRepo, entryRepo, codeGenerator);

        userId = UUID.randomUUID();
        competitionId = UUID.randomUUID();
        seasonId = UUID.randomUUID();
        roundId = UUID.randomUUID();

        phases = List.of(
                RoundSpan.builder()
                        .code("S1")
                        .name("Sprint 1")
                        .type(PhaseType.SPRINT)
                        .from(1)
                        .to(5)
                        .build(),
                RoundSpan.builder()
                        .code("S2")
                        .name("Sprint 2")
                        .type(PhaseType.SPRINT)
                        .from(6)
                        .to(10)
                        .build(),
                RoundSpan.builder()
                        .code("Q1")
                        .name("Quarter 1")
                        .type(PhaseType.QUARTER)
                        .from(1)
                        .to(10)
                        .build(),
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

        competition = Competition.builder()
                .id(competitionId)
                .name("Premier League")
                .slug(CompetitionSlug.of(SLUG))
                .code("PL")
                .phases(phases)
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
                .position(1)
                .name("Round 1")
                .slug("round-1")
                .build();
    }

    @Test
    void happyPath_singleSprint_savesContestAndEntry() {
        when(competitionRepo.findBySlug(SLUG)).thenReturn(Optional.of(competition));
        when(seasonRepo.findActiveSeason(competitionId)).thenReturn(Optional.of(season));
        when(roundRepo.findById(roundId)).thenReturn(Optional.of(currentRound));
        when(roundSupport.resolveStatus(currentRound)).thenReturn(RoundStatus.OPEN);
        when(contestRepo.findPrivateByUserId(userId)).thenReturn(List.of());
        when(contestRepo.findByJoinCode(any())).thenReturn(Optional.empty());
        when(codeGenerator.generate()).thenReturn(CODE);
        when(contestRepo.save(any())).thenAnswer(inv -> {
            Contest c = inv.getArgument(0);
            c.setId(UUID.randomUUID());
            return c;
        });
        when(entryRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        var cmd = new CreatePrivateContestCommand(userId, "My Contest", null, "S1", "S1", SLUG);
        var result = useCase.execute(cmd);

        assertThat(result.isRight()).isTrue();
        assertThat(result.get().joinCode()).isEqualTo(CODE);

        ArgumentCaptor<Contest> contestCaptor = ArgumentCaptor.forClass(Contest.class);
        verify(contestRepo).save(contestCaptor.capture());
        Contest saved = contestCaptor.getValue();
        assertThat(saved.isPrivate()).isTrue();
        assertThat(saved.isOpen()).isTrue();
        assertThat(saved.getOwnerId()).isEqualTo(userId);
        assertThat(saved.getFromRoundPosition()).isEqualTo(1);
        assertThat(saved.getToRoundPosition()).isEqualTo(5);

        ArgumentCaptor<Entry> entryCaptor = ArgumentCaptor.forClass(Entry.class);
        verify(entryRepo).save(entryCaptor.capture());
        Entry entry = entryCaptor.getValue();
        assertThat(entry.getUserId()).isEqualTo(userId);
        assertThat(entry.getJoinedAtRound()).isEqualTo(1);
    }

    @Test
    void happyPath_multiSprint_fullQuarter_savesContest() {
        when(competitionRepo.findBySlug(SLUG)).thenReturn(Optional.of(competition));
        when(seasonRepo.findActiveSeason(competitionId)).thenReturn(Optional.of(season));
        when(roundRepo.findById(roundId)).thenReturn(Optional.of(currentRound));
        when(roundSupport.resolveStatus(currentRound)).thenReturn(RoundStatus.OPEN);
        when(contestRepo.findPrivateByUserId(userId)).thenReturn(List.of());
        when(contestRepo.findByJoinCode(any())).thenReturn(Optional.empty());
        when(codeGenerator.generate()).thenReturn(CODE);
        when(contestRepo.save(any())).thenAnswer(inv -> {
            Contest c = inv.getArgument(0);
            c.setId(UUID.randomUUID());
            return c;
        });
        when(entryRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        var cmd = new CreatePrivateContestCommand(userId, "Q1 Contest", null, "S1", "S2", SLUG);
        var result = useCase.execute(cmd);

        assertThat(result.isRight()).isTrue();
        ArgumentCaptor<Contest> captor = ArgumentCaptor.forClass(Contest.class);
        verify(contestRepo).save(captor.capture());
        assertThat(captor.getValue().getFromRoundPosition()).isEqualTo(1);
        assertThat(captor.getValue().getToRoundPosition()).isEqualTo(10);
    }

    @Test
    void fromSprintNotFound_returnsInvalidFromSprintError() {
        when(competitionRepo.findBySlug(SLUG)).thenReturn(Optional.of(competition));
        when(seasonRepo.findActiveSeason(competitionId)).thenReturn(Optional.of(season));
        when(roundRepo.findById(roundId)).thenReturn(Optional.of(currentRound));

        var cmd = new CreatePrivateContestCommand(userId, "Contest", null, "S99", "S99", SLUG);
        var result = useCase.execute(cmd);

        assertThat(result.isLeft()).isTrue();
        assertThat(result.getLeft()).isInstanceOf(CreatePrivateContestError.InvalidFromSprint.class);
    }

    @Test
    void fromSprintFullyPassed_returnsInvalidFromSprintError() {
        Round roundAtPosition6 = Round.builder()
                .id(roundId)
                .seasonId(seasonId)
                .position(6)
                .name("Round 6")
                .slug("round-6")
                .build();
        when(competitionRepo.findBySlug(SLUG)).thenReturn(Optional.of(competition));
        when(seasonRepo.findActiveSeason(competitionId)).thenReturn(Optional.of(season));
        when(roundRepo.findById(roundId)).thenReturn(Optional.of(roundAtPosition6));

        // S1 ends at round 5; current round is 6 → sprint has passed
        var cmd = new CreatePrivateContestCommand(userId, "Contest", null, "S1", "S1", SLUG);
        var result = useCase.execute(cmd);

        assertThat(result.isLeft()).isTrue();
        assertThat(result.getLeft()).isInstanceOf(CreatePrivateContestError.InvalidFromSprint.class);
    }

    @Test
    void fromSprintCurrentRoundLocked_returnsInvalidFromSprintError() {
        // Current round is round 1 (at sprint start) but round is LOCKED
        when(competitionRepo.findBySlug(SLUG)).thenReturn(Optional.of(competition));
        when(seasonRepo.findActiveSeason(competitionId)).thenReturn(Optional.of(season));
        when(roundRepo.findById(roundId)).thenReturn(Optional.of(currentRound));
        when(roundSupport.resolveStatus(currentRound)).thenReturn(RoundStatus.LOCKED);

        var cmd = new CreatePrivateContestCommand(userId, "Contest", null, "S1", "S1", SLUG);
        var result = useCase.execute(cmd);

        assertThat(result.isLeft()).isTrue();
        assertThat(result.getLeft()).isInstanceOf(CreatePrivateContestError.InvalidFromSprint.class);
    }

    @Test
    void fromSprintMidSprint_returnsInvalidFromSprintError() {
        // Current round is 3 (mid-sprint S1 which spans 1-5)
        Round midSprint = Round.builder()
                .id(roundId)
                .seasonId(seasonId)
                .position(3)
                .name("Round 3")
                .slug("round-3")
                .build();
        when(competitionRepo.findBySlug(SLUG)).thenReturn(Optional.of(competition));
        when(seasonRepo.findActiveSeason(competitionId)).thenReturn(Optional.of(season));
        when(roundRepo.findById(roundId)).thenReturn(Optional.of(midSprint));

        var cmd = new CreatePrivateContestCommand(userId, "Contest", null, "S1", "S1", SLUG);
        var result = useCase.execute(cmd);

        assertThat(result.isLeft()).isTrue();
        assertThat(result.getLeft()).isInstanceOf(CreatePrivateContestError.InvalidFromSprint.class);
    }

    @Test
    void toSprintNotQuarterEnd_multiSprint_returnsInvalidToCombinationError() {
        when(competitionRepo.findBySlug(SLUG)).thenReturn(Optional.of(competition));
        when(seasonRepo.findActiveSeason(competitionId)).thenReturn(Optional.of(season));
        when(roundRepo.findById(roundId)).thenReturn(Optional.of(currentRound));
        when(roundSupport.resolveStatus(currentRound)).thenReturn(RoundStatus.OPEN);

        // S1 → S3: S3 is not a quarter-end (Q1 ends at S2, Q2 ends at S4)
        var cmd = new CreatePrivateContestCommand(userId, "Contest", null, "S1", "S3", SLUG);
        var result = useCase.execute(cmd);

        assertThat(result.isLeft()).isTrue();
        assertThat(result.getLeft()).isInstanceOf(CreatePrivateContestError.InvalidToCombination.class);
    }

    @Test
    void fromSprintNotQuarterStart_multiSprint_returnsInvalidFromSprintError() {
        // Current round is at the start of S3 (position 11)
        Round roundAtS3 = Round.builder()
                .id(roundId)
                .seasonId(seasonId)
                .position(11)
                .name("Round 11")
                .slug("round-11")
                .build();
        when(competitionRepo.findBySlug(SLUG)).thenReturn(Optional.of(competition));
        when(seasonRepo.findActiveSeason(competitionId)).thenReturn(Optional.of(season));
        when(roundRepo.findById(roundId)).thenReturn(Optional.of(roundAtS3));

        // S2 → S4: S2 is not a quarter-start (Q1 starts at S1, Q2 starts at S3) → InvalidFromSprint
        var cmd = new CreatePrivateContestCommand(userId, "Contest", null, "S2", "S4", SLUG);
        var result = useCase.execute(cmd);

        assertThat(result.isLeft()).isTrue();
        assertThat(result.getLeft()).isInstanceOf(CreatePrivateContestError.InvalidFromSprint.class);
    }

    @Test
    void maxContestsReached_returnsError() {
        when(competitionRepo.findBySlug(SLUG)).thenReturn(Optional.of(competition));
        when(seasonRepo.findActiveSeason(competitionId)).thenReturn(Optional.of(season));
        when(roundRepo.findById(roundId)).thenReturn(Optional.of(currentRound));
        when(roundSupport.resolveStatus(currentRound)).thenReturn(RoundStatus.OPEN);

        List<Contest> twentyContests = java.util.Collections.nCopies(
                20, Contest.builder().seasonId(seasonId).name("x").build());
        when(contestRepo.findPrivateByUserId(userId)).thenReturn(twentyContests);

        var cmd = new CreatePrivateContestCommand(userId, "Contest", null, "S1", "S1", SLUG);
        var result = useCase.execute(cmd);

        assertThat(result.isLeft()).isTrue();
        assertThat(result.getLeft()).isInstanceOf(CreatePrivateContestError.MaxContestsReached.class);
    }

    @Test
    void codeCollisionRetry_secondCodeIsSaved() {
        when(competitionRepo.findBySlug(SLUG)).thenReturn(Optional.of(competition));
        when(seasonRepo.findActiveSeason(competitionId)).thenReturn(Optional.of(season));
        when(roundRepo.findById(roundId)).thenReturn(Optional.of(currentRound));
        when(roundSupport.resolveStatus(currentRound)).thenReturn(RoundStatus.OPEN);
        when(contestRepo.findPrivateByUserId(userId)).thenReturn(List.of());

        Contest existing = Contest.builder()
                .id(UUID.randomUUID())
                .seasonId(seasonId)
                .name("x")
                .build();
        when(contestRepo.findByJoinCode("FIRST11")).thenReturn(Optional.of(existing));
        when(contestRepo.findByJoinCode("SECOND2")).thenReturn(Optional.empty());
        when(codeGenerator.generate()).thenReturn("FIRST11", "SECOND2");
        when(contestRepo.save(any())).thenAnswer(inv -> {
            Contest c = inv.getArgument(0);
            c.setId(UUID.randomUUID());
            return c;
        });
        when(entryRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        var cmd = new CreatePrivateContestCommand(userId, "Contest", null, "S1", "S1", SLUG);
        var result = useCase.execute(cmd);

        assertThat(result.isRight()).isTrue();
        assertThat(result.get().joinCode()).isEqualTo("SECOND2");
    }

    @Test
    void competitionNotFound_returnsError() {
        when(competitionRepo.findBySlug(SLUG)).thenReturn(Optional.empty());

        var cmd = new CreatePrivateContestCommand(userId, "Contest", null, "S1", "S1", SLUG);
        var result = useCase.execute(cmd);

        assertThat(result.isLeft()).isTrue();
        assertThat(result.getLeft()).isInstanceOf(CreatePrivateContestError.CompetitionNotFound.class);
    }
}
