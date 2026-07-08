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

/** Uses the real Premier League phase structure (see {@link CompetitionPhaseFixtures}). */
@ExtendWith(MockitoExtension.class)
class RenewContestUseCaseTest {

    @Mock
    ContestRepo contestRepo;

    @Mock
    EntryRepo entryRepo;

    @Mock
    SeasonRepo seasonRepo;

    @Mock
    RoundRepo roundRepo;

    @Mock
    CompetitionRepo competitionRepo;

    @Mock
    ContestCodeGenerator codeGenerator;

    private RenewContestUseCase useCase;

    private static final String CODE = "AB3K7PQ";

    private UUID userId;
    private UUID competitionId;
    private UUID seasonId;
    private UUID currentRoundId;
    private List<RoundSpan> phases;
    private Competition competition;
    private Season season;

    @BeforeEach
    void setUp() {
        useCase =
                new RenewContestUseCase(contestRepo, entryRepo, seasonRepo, roundRepo, competitionRepo, codeGenerator);

        userId = UUID.randomUUID();
        competitionId = UUID.randomUUID();
        seasonId = UUID.randomUUID();
        currentRoundId = UUID.randomUUID();
        phases = CompetitionPhaseFixtures.phases();

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
                .maxRounds(38)
                .currentRoundId(currentRoundId)
                .build();
    }

    /** Stubs the current round position, used to satisfy the current-season renewal timing gate. */
    private void stubCurrentRoundPosition(int position) {
        when(roundRepo.findPosition(currentRoundId)).thenReturn(position);
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
    void happyPath_singleSprint_savesRenewedContestAndCopiesActiveMembers() {
        Contest original = originalContest(25, 29); // S6 -> S6
        when(contestRepo.findById(original.getId())).thenReturn(Optional.of(original));
        stubActiveSeasonSameAsContest();
        stubCurrentRoundPosition(35); // S8 — 2 sprints past the original's own sprint (S6), timing gate met
        when(contestRepo.findByJoinCode(any())).thenReturn(Optional.empty());
        when(codeGenerator.generate()).thenReturn(CODE);

        when(contestRepo.save(any())).thenAnswer(inv -> {
            Contest c = inv.getArgument(0);
            if (c.getId() == null) c.setId(UUID.randomUUID());
            return c;
        });

        var cmd = new RenewContestCommand(userId, original.getId(), "S7");
        var result = useCase.execute(cmd);

        assertThat(result.isRight()).isTrue();
        assertThat(result.get().joinCode()).isEqualTo(CODE);

        ArgumentCaptor<Contest> contestCaptor = ArgumentCaptor.forClass(Contest.class);
        verify(contestRepo, times(2)).save(contestCaptor.capture());
        Contest savedRenewed = contestCaptor.getAllValues().get(0);
        assertThat(savedRenewed.getFromRoundPosition()).isEqualTo(30);
        assertThat(savedRenewed.getToRoundPosition()).isEqualTo(34);
        assertThat(savedRenewed.getName()).isEqualTo("Office Rivals");
        assertThat(savedRenewed.isOpen()).isTrue();
        assertThat(savedRenewed.getMaxEntries()).isEqualTo(10);
        assertThat(savedRenewed.getOwnerId()).isEqualTo(userId);

        Contest savedOriginal = contestCaptor.getAllValues().get(1);
        assertThat(savedOriginal.getRenewedIntoContestId()).isEqualTo(savedRenewed.getId());

        verify(entryRepo)
                .copyActiveEntries(original.getId(), savedRenewed.getId(), savedRenewed.getFromRoundPosition());
    }

    @Test
    void notOwner_returnsError() {
        Contest original = originalContest(1, 9);
        when(contestRepo.findById(original.getId())).thenReturn(Optional.of(original));

        var cmd = new RenewContestCommand(UUID.randomUUID(), original.getId(), "S3");
        var result = useCase.execute(cmd);

        assertThat(result.isLeft()).isTrue();
        assertThat(result.getLeft()).isInstanceOf(RenewContestError.NotOwner.class);
    }

    @Test
    void notPrivate_returnsError() {
        Contest original = originalContest(1, 9);
        original.setPrivate(false);
        when(contestRepo.findById(original.getId())).thenReturn(Optional.of(original));

        var cmd = new RenewContestCommand(userId, original.getId(), "S3");
        var result = useCase.execute(cmd);

        assertThat(result.isLeft()).isTrue();
        assertThat(result.getLeft()).isInstanceOf(RenewContestError.NotPrivate.class);
    }

    @Test
    void tooEarly_currentSeason_singleSprint_returnsError() {
        Contest original = originalContest(1, 4); // S1 -> S1
        when(contestRepo.findById(original.getId())).thenReturn(Optional.of(original));
        stubActiveSeasonSameAsContest();
        stubCurrentRoundPosition(2); // GW2 — one round short of the GW3 timing threshold (S1.from + 2)

        var cmd = new RenewContestCommand(userId, original.getId(), "S2");
        var result = useCase.execute(cmd);

        assertThat(result.isLeft()).isTrue();
        assertThat(result.getLeft()).isInstanceOf(RenewContestError.TooEarly.class);
    }

    @Test
    void tooEarly_currentSeason_multiSprint_returnsError() {
        Contest original = originalContest(1, 9); // S1 -> S2 (Q1), its own last sprint is S2
        when(contestRepo.findById(original.getId())).thenReturn(Optional.of(original));
        stubActiveSeasonSameAsContest();
        stubCurrentRoundPosition(1); // GW1, within S1 — final leg (S2, GW5-9) not underway yet

        var cmd = new RenewContestCommand(userId, original.getId(), "S3");
        var result = useCase.execute(cmd);

        assertThat(result.isLeft()).isTrue();
        assertThat(result.getLeft()).isInstanceOf(RenewContestError.TooEarly.class);
    }

    @Test
    void alreadyRenewed_returnsError() {
        Contest original = originalContest(1, 9);
        original.setRenewedIntoContestId(UUID.randomUUID());
        when(contestRepo.findById(original.getId())).thenReturn(Optional.of(original));

        var cmd = new RenewContestCommand(userId, original.getId(), "S3");
        var result = useCase.execute(cmd);

        assertThat(result.isLeft()).isTrue();
        assertThat(result.getLeft()).isInstanceOf(RenewContestError.AlreadyRenewed.class);
    }

    @Test
    void fullSeason_returnsNotRenewable() {
        Contest original = originalContest(1, 38); // S1 -> S8
        when(contestRepo.findById(original.getId())).thenReturn(Optional.of(original));
        stubActiveSeasonSameAsContest();

        var cmd = new RenewContestCommand(userId, original.getId(), "S8");
        var result = useCase.execute(cmd);

        assertThat(result.isLeft()).isTrue();
        assertThat(result.getLeft()).isInstanceOf(RenewContestError.NotRenewable.class);
    }

    @Test
    void pastSeason_noActiveSeason_returnsNotRenewable() {
        Contest original = originalContest(1, 9);
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
        Contest original = originalContest(30, 38); // S7 -> S8 (Q4) in the past season
        when(contestRepo.findById(original.getId())).thenReturn(Optional.of(original));
        when(seasonRepo.findById(seasonId)).thenReturn(Optional.of(season));
        when(competitionRepo.findById(competitionId)).thenReturn(Optional.of(competition));

        Season activeSeason = Season.builder()
                .id(UUID.randomUUID())
                .competitionId(competitionId)
                .build();
        when(seasonRepo.findActiveSeason(competitionId)).thenReturn(Optional.of(activeSeason));
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
        assertThat(savedRenewed.getToRoundPosition()).isEqualTo(9);
    }

    @Test
    void pastSeason_fullSeasonOriginal_onlyS8IsValidTo() {
        Contest original = originalContest(1, 38); // S1 -> S8 full season in the past season
        when(contestRepo.findById(original.getId())).thenReturn(Optional.of(original));
        when(seasonRepo.findById(seasonId)).thenReturn(Optional.of(season));
        when(competitionRepo.findById(competitionId)).thenReturn(Optional.of(competition));

        Season activeSeason = Season.builder()
                .id(UUID.randomUUID())
                .competitionId(competitionId)
                .build();
        when(seasonRepo.findActiveSeason(competitionId)).thenReturn(Optional.of(activeSeason));

        // TO fixed at S8 for a renewed full season — S2 must be rejected.
        var cmd = new RenewContestCommand(userId, original.getId(), "S2");
        var result = useCase.execute(cmd);

        assertThat(result.isLeft()).isTrue();
        assertThat(result.getLeft()).isInstanceOf(RenewContestError.InvalidToCombination.class);
    }

    @Test
    void pastSeason_fullSeasonOriginal_s8IsAccepted() {
        Contest original = originalContest(1, 38);
        when(contestRepo.findById(original.getId())).thenReturn(Optional.of(original));
        when(seasonRepo.findById(seasonId)).thenReturn(Optional.of(season));
        when(competitionRepo.findById(competitionId)).thenReturn(Optional.of(competition));

        Season activeSeason = Season.builder()
                .id(UUID.randomUUID())
                .competitionId(competitionId)
                .build();
        when(seasonRepo.findActiveSeason(competitionId)).thenReturn(Optional.of(activeSeason));
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
        assertThat(savedRenewed.getToRoundPosition()).isEqualTo(38);
    }

    @Test
    void endOfSeason_noSprintRemains_returnsNotRenewable() {
        Contest original = originalContest(30, 38); // S7 -> S8, Q4
        when(contestRepo.findById(original.getId())).thenReturn(Optional.of(original));
        stubActiveSeasonSameAsContest();

        var cmd = new RenewContestCommand(userId, original.getId(), "S8");
        var result = useCase.execute(cmd);

        assertThat(result.isLeft()).isTrue();
        assertThat(result.getLeft()).isInstanceOf(RenewContestError.NotRenewable.class);
    }

    @Test
    void invalidToCombination_returnsError() {
        Contest original = originalContest(1, 4); // S1 -> S1, from = S2
        when(contestRepo.findById(original.getId())).thenReturn(Optional.of(original));
        stubActiveSeasonSameAsContest();
        stubCurrentRoundPosition(3); // GW3 — meets the GW3 timing threshold (S1.from + 2)

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
        Contest original = originalContest(25, 29);
        when(contestRepo.findById(original.getId())).thenReturn(Optional.of(original));
        stubActiveSeasonSameAsContest();
        stubCurrentRoundPosition(35);

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
            if (c.getId() == null) c.setId(UUID.randomUUID());
            return c;
        });

        var cmd = new RenewContestCommand(userId, original.getId(), "S7");
        var result = useCase.execute(cmd);

        assertThat(result.isRight()).isTrue();
        assertThat(result.get().joinCode()).isEqualTo("SECOND2");
    }

    @Test
    void nameConflict_ownerAlreadyHasContestWithRenewedName_returnsError() {
        // Owner already has another contest named "Office Rivals" occupying S7 in this season.
        Contest original = originalContest(25, 29); // S6 -> S6, renews to S7
        when(contestRepo.findById(original.getId())).thenReturn(Optional.of(original));
        stubActiveSeasonSameAsContest();
        stubCurrentRoundPosition(35);
        when(contestRepo.existsByOwnerSeasonPeriodAndName(seasonId, userId, 30, 34, "Office Rivals", null))
                .thenReturn(true);

        var cmd = new RenewContestCommand(userId, original.getId(), "S7");
        var result = useCase.execute(cmd);

        assertThat(result.isLeft()).isTrue();
        assertThat(result.getLeft()).isInstanceOf(RenewContestError.NameConflict.class);
        verify(contestRepo, never()).save(any());
    }
}
