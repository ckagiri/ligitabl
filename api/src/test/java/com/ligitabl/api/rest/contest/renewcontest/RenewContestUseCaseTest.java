package com.ligitabl.api.rest.contest.renewcontest;

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
import com.ligitabl.model.domain.*;
import com.ligitabl.model.repo.*;

/**
 * Phases mirror a season's S1-S8 / Q1-Q4 structure, one round per sprint: S1=round1 ...
 * S8=round8, Q1=S1+S2, Q2=S3+S4, Q3=S5+S6, Q4=S7+S8.
 */
@ExtendWith(MockitoExtension.class)
class RenewContestUseCaseTest {

    @Mock
    ContestRepo contestRepo;

    @Mock
    EntryRepo entryRepo;

    @Mock
    SeasonRepo seasonRepo;

    @Mock
    CompetitionRepo competitionRepo;

    @Mock
    ContestCodeGenerator codeGenerator;

    private RenewContestUseCase useCase;

    private static final String CODE = "AB3K7PQ";

    private UUID userId;
    private UUID competitionId;
    private UUID seasonId;
    private List<RoundSpan> phases;
    private Competition competition;
    private Season season;

    @BeforeEach
    void setUp() {
        useCase = new RenewContestUseCase(contestRepo, entryRepo, seasonRepo, competitionRepo, codeGenerator);

        userId = UUID.randomUUID();
        competitionId = UUID.randomUUID();
        seasonId = UUID.randomUUID();
        phases = buildPhases();

        competition = Competition.builder()
                .id(competitionId)
                .name("Premier League")
                .slug(CompetitionSlug.of("premier-league"))
                .code("PL")
                .phases(phases)
                .build();

        season = Season.builder()
                .id(seasonId)
                .competitionId(competitionId)
                .name("2025/26")
                .slug(SeasonSlug.of("2025-26"))
                .clientId(1)
                .maxRounds(8)
                .build();
    }

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
        List<RoundSpan> quarters = List.of(
                quarter("Q1", 1, 2), quarter("Q2", 3, 4), quarter("Q3", 5, 6), quarter("Q4", 7, 8));
        return java.util.stream.Stream.concat(sprints.stream(), quarters.stream()).toList();
    }

    private static RoundSpan sprint(String code, int from, int to) {
        return RoundSpan.builder().code(code).name(code).type(PhaseType.SPRINT).from(from).to(to).build();
    }

    private static RoundSpan quarter(String code, int from, int to) {
        return RoundSpan.builder().code(code).name(code).type(PhaseType.QUARTER).from(from).to(to).build();
    }

    private Contest originalContest(int fromRoundPosition, int toRoundPosition) {
        return Contest.builder()
                .id(UUID.randomUUID())
                .seasonId(seasonId)
                .name("Office Rivals")
                .isPrivate(true)
                .isOpen(true)
                .joinCode("ORIGINAL")
                .fromRoundPosition(fromRoundPosition)
                .toRoundPosition(toRoundPosition)
                .maxEntries(10)
                .ownerId(userId)
                .build();
    }

    private void stubActiveSeasonSameAsContest() {
        when(seasonRepo.findById(seasonId)).thenReturn(Optional.of(season));
        when(competitionRepo.findById(competitionId)).thenReturn(Optional.of(competition));
        when(seasonRepo.findActiveSeason(competitionId)).thenReturn(Optional.of(season));
    }

    @Test
    void happyPath_singleSprint_savesRenewedContestAndCarriesOverActiveMembers() {
        Contest original = originalContest(6, 6); // S6 -> S6
        when(contestRepo.findById(original.getId())).thenReturn(Optional.of(original));
        stubActiveSeasonSameAsContest();
        when(contestRepo.findByJoinCode(any())).thenReturn(Optional.empty());
        when(codeGenerator.generate()).thenReturn(CODE);

        UUID activeUser = UUID.randomUUID();
        UUID leftUser = UUID.randomUUID();
        when(entryRepo.findByContestId(original.getId()))
                .thenReturn(List.of(
                        Entry.builder()
                                .userId(activeUser)
                                .contestId(original.getId())
                                .joinedAtRound(1)
                                .build(),
                        Entry.builder()
                                .userId(leftUser)
                                .contestId(original.getId())
                                .joinedAtRound(1)
                                .removedAtRound(3)
                                .build()));

        when(contestRepo.save(any())).thenAnswer(inv -> {
            Contest c = inv.getArgument(0);
            if (c.getId() == null) c.setId(UUID.randomUUID());
            return c;
        });
        when(entryRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        var cmd = new RenewContestCommand(userId, original.getId(), "S7");
        var result = useCase.execute(cmd);

        assertThat(result.isRight()).isTrue();
        assertThat(result.get().joinCode()).isEqualTo(CODE);

        ArgumentCaptor<Contest> contestCaptor = ArgumentCaptor.forClass(Contest.class);
        verify(contestRepo, times(2)).save(contestCaptor.capture());
        Contest savedRenewed = contestCaptor.getAllValues().get(0);
        assertThat(savedRenewed.getFromRoundPosition()).isEqualTo(7);
        assertThat(savedRenewed.getToRoundPosition()).isEqualTo(7);
        assertThat(savedRenewed.getName()).isEqualTo("Office Rivals");
        assertThat(savedRenewed.isOpen()).isTrue();
        assertThat(savedRenewed.getMaxEntries()).isEqualTo(10);
        assertThat(savedRenewed.getOwnerId()).isEqualTo(userId);

        Contest savedOriginal = contestCaptor.getAllValues().get(1);
        assertThat(savedOriginal.getRenewedIntoContestId()).isEqualTo(savedRenewed.getId());

        ArgumentCaptor<Entry> entryCaptor = ArgumentCaptor.forClass(Entry.class);
        verify(entryRepo, times(1)).save(entryCaptor.capture());
        assertThat(entryCaptor.getValue().getUserId()).isEqualTo(activeUser);
        assertThat(entryCaptor.getValue().getContestId()).isEqualTo(savedRenewed.getId());
    }

    @Test
    void notOwner_returnsError() {
        Contest original = originalContest(1, 2);
        when(contestRepo.findById(original.getId())).thenReturn(Optional.of(original));

        var cmd = new RenewContestCommand(UUID.randomUUID(), original.getId(), "S3");
        var result = useCase.execute(cmd);

        assertThat(result.isLeft()).isTrue();
        assertThat(result.getLeft()).isInstanceOf(RenewContestError.NotOwner.class);
    }

    @Test
    void alreadyRenewed_returnsError() {
        Contest original = originalContest(1, 2);
        original.setRenewedIntoContestId(UUID.randomUUID());
        when(contestRepo.findById(original.getId())).thenReturn(Optional.of(original));

        var cmd = new RenewContestCommand(userId, original.getId(), "S3");
        var result = useCase.execute(cmd);

        assertThat(result.isLeft()).isTrue();
        assertThat(result.getLeft()).isInstanceOf(RenewContestError.AlreadyRenewed.class);
    }

    @Test
    void fullSeason_returnsNotRenewable() {
        Contest original = originalContest(1, 8); // S1 -> S8
        when(contestRepo.findById(original.getId())).thenReturn(Optional.of(original));
        stubActiveSeasonSameAsContest();

        var cmd = new RenewContestCommand(userId, original.getId(), "S8");
        var result = useCase.execute(cmd);

        assertThat(result.isLeft()).isTrue();
        assertThat(result.getLeft()).isInstanceOf(RenewContestError.NotRenewable.class);
    }

    @Test
    void pastSeason_noActiveSeason_returnsNotRenewable() {
        Contest original = originalContest(1, 2);
        when(contestRepo.findById(original.getId())).thenReturn(Optional.of(original));
        when(seasonRepo.findById(seasonId)).thenReturn(Optional.of(season));
        when(competitionRepo.findById(competitionId)).thenReturn(Optional.of(competition));
        when(seasonRepo.findActiveSeason(competitionId)).thenReturn(Optional.empty());

        var cmd = new RenewContestCommand(userId, original.getId(), "S1");
        var result = useCase.execute(cmd);

        assertThat(result.isLeft()).isTrue();
        assertThat(result.getLeft()).isInstanceOf(RenewContestError.NotRenewable.class);
    }

    @Test
    void pastSeason_partialOriginal_defaultsToEndOfQ1_savesIntoActiveSeason() {
        Contest original = originalContest(7, 8); // S7 -> S8 (Q4) in the past season
        when(contestRepo.findById(original.getId())).thenReturn(Optional.of(original));
        when(seasonRepo.findById(seasonId)).thenReturn(Optional.of(season));
        when(competitionRepo.findById(competitionId)).thenReturn(Optional.of(competition));

        Season activeSeason =
                Season.builder().id(UUID.randomUUID()).competitionId(competitionId).build();
        when(seasonRepo.findActiveSeason(competitionId)).thenReturn(Optional.of(activeSeason));
        when(entryRepo.findByContestId(original.getId())).thenReturn(List.of());
        when(contestRepo.findByJoinCode(any())).thenReturn(Optional.empty());
        when(codeGenerator.generate()).thenReturn(CODE);
        when(contestRepo.save(any())).thenAnswer(inv -> {
            Contest c = inv.getArgument(0);
            if (c.getId() == null) c.setId(UUID.randomUUID());
            return c;
        });

        // Default TO for a past-season renewal is end of Q1 (S2), per spec — used here as the chosen TO.
        var cmd = new RenewContestCommand(userId, original.getId(), "S2");
        var result = useCase.execute(cmd);

        assertThat(result.isRight()).isTrue();
        ArgumentCaptor<Contest> captor = ArgumentCaptor.forClass(Contest.class);
        verify(contestRepo, times(2)).save(captor.capture());
        Contest savedRenewed = captor.getAllValues().get(0);
        assertThat(savedRenewed.getSeasonId()).isEqualTo(activeSeason.getId());
        assertThat(savedRenewed.getFromRoundPosition()).isEqualTo(1);
        assertThat(savedRenewed.getToRoundPosition()).isEqualTo(2);
    }

    @Test
    void pastSeason_fullSeasonOriginal_onlyS8IsValidTo() {
        Contest original = originalContest(1, 8); // S1 -> S8 full season in the past season
        when(contestRepo.findById(original.getId())).thenReturn(Optional.of(original));
        when(seasonRepo.findById(seasonId)).thenReturn(Optional.of(season));
        when(competitionRepo.findById(competitionId)).thenReturn(Optional.of(competition));

        Season activeSeason =
                Season.builder().id(UUID.randomUUID()).competitionId(competitionId).build();
        when(seasonRepo.findActiveSeason(competitionId)).thenReturn(Optional.of(activeSeason));

        // TO fixed at S8 for a renewed full season — S2 must be rejected.
        var cmd = new RenewContestCommand(userId, original.getId(), "S2");
        var result = useCase.execute(cmd);

        assertThat(result.isLeft()).isTrue();
        assertThat(result.getLeft()).isInstanceOf(RenewContestError.InvalidToCombination.class);
    }

    @Test
    void pastSeason_fullSeasonOriginal_s8IsAccepted() {
        Contest original = originalContest(1, 8);
        when(contestRepo.findById(original.getId())).thenReturn(Optional.of(original));
        when(seasonRepo.findById(seasonId)).thenReturn(Optional.of(season));
        when(competitionRepo.findById(competitionId)).thenReturn(Optional.of(competition));

        Season activeSeason =
                Season.builder().id(UUID.randomUUID()).competitionId(competitionId).build();
        when(seasonRepo.findActiveSeason(competitionId)).thenReturn(Optional.of(activeSeason));
        when(entryRepo.findByContestId(original.getId())).thenReturn(List.of());
        when(contestRepo.findByJoinCode(any())).thenReturn(Optional.empty());
        when(codeGenerator.generate()).thenReturn(CODE);
        when(contestRepo.save(any())).thenAnswer(inv -> {
            Contest c = inv.getArgument(0);
            if (c.getId() == null) c.setId(UUID.randomUUID());
            return c;
        });

        var cmd = new RenewContestCommand(userId, original.getId(), "S8");
        var result = useCase.execute(cmd);

        assertThat(result.isRight()).isTrue();
        ArgumentCaptor<Contest> captor = ArgumentCaptor.forClass(Contest.class);
        verify(contestRepo, times(2)).save(captor.capture());
        Contest savedRenewed = captor.getAllValues().get(0);
        assertThat(savedRenewed.getFromRoundPosition()).isEqualTo(1);
        assertThat(savedRenewed.getToRoundPosition()).isEqualTo(8);
    }

    @Test
    void endOfSeason_noSprintRemains_returnsNotRenewable() {
        Contest original = originalContest(7, 8); // S7 -> S8, Q4
        when(contestRepo.findById(original.getId())).thenReturn(Optional.of(original));
        stubActiveSeasonSameAsContest();

        var cmd = new RenewContestCommand(userId, original.getId(), "S8");
        var result = useCase.execute(cmd);

        assertThat(result.isLeft()).isTrue();
        assertThat(result.getLeft()).isInstanceOf(RenewContestError.NotRenewable.class);
    }

    @Test
    void invalidToCombination_returnsError() {
        Contest original = originalContest(1, 1); // S1 -> S1, from = S2
        when(contestRepo.findById(original.getId())).thenReturn(Optional.of(original));
        stubActiveSeasonSameAsContest();

        // S3 is not a valid TO for FROM=S2 (not S2 itself and not a quarter-end reachable from S2)
        var cmd = new RenewContestCommand(userId, original.getId(), "S3");
        var result = useCase.execute(cmd);

        assertThat(result.isLeft()).isTrue();
        assertThat(result.getLeft()).isInstanceOf(RenewContestError.InvalidToCombination.class);
    }

    @Test
    void contestNotFound_returnsError() {
        UUID missingId = UUID.randomUUID();
        when(contestRepo.findById(missingId)).thenReturn(Optional.empty());

        var cmd = new RenewContestCommand(userId, missingId, "S1");
        var result = useCase.execute(cmd);

        assertThat(result.isLeft()).isTrue();
        assertThat(result.getLeft()).isInstanceOf(RenewContestError.ContestNotFound.class);
    }

    @Test
    void codeCollisionRetry_secondCodeIsSaved() {
        Contest original = originalContest(6, 6);
        when(contestRepo.findById(original.getId())).thenReturn(Optional.of(original));
        stubActiveSeasonSameAsContest();
        when(entryRepo.findByContestId(original.getId())).thenReturn(List.of());

        Contest existing = Contest.builder().id(UUID.randomUUID()).seasonId(seasonId).name("x").build();
        when(contestRepo.findByJoinCode("FIRST11")).thenReturn(Optional.of(existing));
        when(contestRepo.findByJoinCode("SECOND2")).thenReturn(Optional.empty());
        when(codeGenerator.generate()).thenReturn("FIRST11", "SECOND2");
        when(contestRepo.save(any())).thenAnswer(inv -> {
            Contest c = inv.getArgument(0);
            if (c.getId() == null) c.setId(UUID.randomUUID());
            return c;
        });

        var cmd = new RenewContestCommand(userId, original.getId(), "S7");
        var result = useCase.execute(cmd);

        assertThat(result.isRight()).isTrue();
        assertThat(result.get().joinCode()).isEqualTo("SECOND2");
    }
}
