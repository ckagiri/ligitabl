package com.ligitabl.api.rest.prediction.roundopeningswap;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.*;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.ligitabl.api.config.CompetitionDefaults;
import com.ligitabl.api.rest.prediction.makeswap.SwapCommand;
import com.ligitabl.api.rest.prediction.makeswap.SwapError;
import com.ligitabl.api.rest.prediction.shared.SwapHelper;
import com.ligitabl.model.domain.Match;
import com.ligitabl.model.domain.MatchStatus;
import com.ligitabl.model.domain.Round;
import com.ligitabl.model.domain.Season;
import com.ligitabl.model.domain.SeasonPrediction;
import com.ligitabl.model.domain.TeamRank;
import com.ligitabl.model.repo.MatchRepo;
import com.ligitabl.model.repo.RoundRepo;
import com.ligitabl.model.repo.SeasonPredictionRepo;
import com.ligitabl.model.repo.SeasonRepo;

@ExtendWith(MockitoExtension.class)
class RoundOpeningSwapUseCaseTest {

    private final CompetitionDefaults competitionDefaults = new CompetitionDefaults("premier-league");

    @Mock
    private SeasonPredictionRepo predictionRepo;

    @Mock
    private SeasonRepo seasonRepo;

    @Mock
    private RoundRepo roundRepo;

    @Mock
    private MatchRepo matchRepo;

    @Mock
    private Clock clock;

    private RoundOpeningSwapUseCase useCase;

    private Instant now;
    private UUID userId;
    private UUID seasonId;
    private UUID roundId;

    private Season season;
    private Round round;
    private SeasonPrediction prediction;

    @BeforeEach
    void setUp() {
        now = Instant.parse("2024-12-22T10:00:00Z");

        userId = UUID.randomUUID();
        seasonId = UUID.randomUUID();
        roundId = UUID.randomUUID();

        season = createSeason();
        round = createRound(5);
        prediction = createPrediction();

        useCase = new RoundOpeningSwapUseCase(
                predictionRepo,
                clock,
                new SwapHelper(competitionDefaults, seasonRepo, roundRepo, predictionRepo, matchRepo));
    }

    // ── Happy path ────────────────────────────────────────────────────────────

    @Test
    void shouldApplySingleSwap_whenOpeningWindowAvailable() {
        var command = new RoundOpeningSwapCommand(List.of(new SwapCommand("ARS", "LIV")));

        stubHappyPath();
        when(predictionRepo.save(any())).thenAnswer(i -> i.getArgument(0));

        var result = useCase.execute(userId, command);

        assertTrue(result.isRight());
        var swapResult = result.get();
        assertTrue(swapResult.success());
        assertEquals(1, swapResult.swapsApplied());

        verify(predictionRepo)
                .save(argThat(p -> p.getOpeningCommittedRound() == round.getPosition()
                        && now.equals(p.getLastSwapAt())
                        && p.getCurrentRankings().stream()
                                .anyMatch(t -> t.getCode().equals("ARS") && t.getPosition() == 2)
                        && p.getCurrentRankings().stream()
                                .anyMatch(t -> t.getCode().equals("LIV") && t.getPosition() == 1)));
    }

    @Test
    void shouldApplyMultipleSwaps_upToTwo() {
        var command =
                new RoundOpeningSwapCommand(List.of(new SwapCommand("ARS", "LIV"), new SwapCommand("MCI", "CHE")));

        season = createSeasonWithRankings(
                List.of(TeamRank.of("ARS", 1), TeamRank.of("LIV", 2), TeamRank.of("MCI", 3), TeamRank.of("CHE", 4)));
        prediction = createPredictionWithRankings(
                List.of(TeamRank.of("ARS", 1), TeamRank.of("LIV", 2), TeamRank.of("MCI", 3), TeamRank.of("CHE", 4)));

        when(clock.instant()).thenReturn(now);
        when(seasonRepo.findActiveSeason("premier-league")).thenReturn(Optional.of(season));
        when(roundRepo.findById(season.getCurrentRoundId())).thenReturn(Optional.of(round));
        when(matchRepo.findByRoundId(round.getId())).thenReturn(List.of());
        when(predictionRepo.findByUserAndSeason(userId, seasonId)).thenReturn(Optional.of(prediction));
        when(predictionRepo.save(any())).thenAnswer(i -> i.getArgument(0));

        var result = useCase.execute(userId, command);

        assertTrue(result.isRight());
        assertEquals(2, result.get().swapsApplied());
    }

    @Test
    void shouldCommitOpeningWindow_andSetLastSwapAt() {
        var command = new RoundOpeningSwapCommand(List.of(new SwapCommand("ARS", "LIV")));

        stubHappyPath();
        when(predictionRepo.save(any())).thenAnswer(i -> i.getArgument(0));

        useCase.execute(userId, command);

        verify(predictionRepo)
                .save(argThat(
                        p -> p.getOpeningCommittedRound() == round.getPosition() && now.equals(p.getLastSwapAt())));
    }

    @Test
    void shouldNormaliseTeamCodes_toLowerOrUpperCase() {
        var command = new RoundOpeningSwapCommand(List.of(new SwapCommand("ars", "liv")));

        stubHappyPath();
        when(predictionRepo.save(any())).thenAnswer(i -> i.getArgument(0));

        var result = useCase.execute(userId, command);

        assertTrue(result.isRight());
    }

    // ── Guard: season ─────────────────────────────────────────────────────────

    @Test
    void shouldReject_whenSeasonCompleted() {
        when(seasonRepo.findActiveSeason("premier-league")).thenReturn(Optional.empty());

        var command = new RoundOpeningSwapCommand(List.of(new SwapCommand("ARS", "LIV")));
        var result = useCase.execute(userId, command);

        assertTrue(result.isLeft());
        assertInstanceOf(SwapError.SeasonCompleted.class, result.getLeft());
        verify(predictionRepo, never()).save(any());
    }

    @Test
    void shouldReject_whenSeasonInSetupMode() {
        season.enterSetupMode();
        when(seasonRepo.findActiveSeason("premier-league")).thenReturn(Optional.of(season));

        var command = new RoundOpeningSwapCommand(List.of(new SwapCommand("ARS", "LIV")));
        var result = useCase.execute(userId, command);

        assertTrue(result.isLeft());
        assertInstanceOf(SwapError.SeasonInSetupMode.class, result.getLeft());
        verify(predictionRepo, never()).save(any());
    }

    // ── Guard: round state ────────────────────────────────────────────────────

    @Test
    void shouldReject_whenRoundNotOpen() {
        when(seasonRepo.findActiveSeason("premier-league")).thenReturn(Optional.of(season));
        when(roundRepo.findById(season.getCurrentRoundId())).thenReturn(Optional.of(round));
        when(matchRepo.findByRoundId(round.getId()))
                .thenReturn(List.of(Match.builder().status(MatchStatus.LIVE).build()));

        var command = new RoundOpeningSwapCommand(List.of(new SwapCommand("ARS", "LIV")));
        var result = useCase.execute(userId, command);

        assertTrue(result.isLeft());
        assertInstanceOf(SwapError.RoundNotOpen.class, result.getLeft());
        verify(predictionRepo, never()).save(any());
    }

    // ── Guard: opening window ─────────────────────────────────────────────────

    @Test
    void shouldReject_whenOpeningAlreadyUsedThisRound() {
        prediction.setOpeningCommittedRound(round.getPosition()); // already committed

        when(seasonRepo.findActiveSeason("premier-league")).thenReturn(Optional.of(season));
        when(roundRepo.findById(season.getCurrentRoundId())).thenReturn(Optional.of(round));
        when(matchRepo.findByRoundId(round.getId())).thenReturn(List.of());
        when(predictionRepo.findByUserAndSeason(userId, seasonId)).thenReturn(Optional.of(prediction));

        var command = new RoundOpeningSwapCommand(List.of(new SwapCommand("ARS", "LIV")));
        var result = useCase.execute(userId, command);

        assertTrue(result.isLeft());
        var error = assertInstanceOf(SwapError.OpeningAlreadyUsed.class, result.getLeft());
        assertEquals(round.getPosition(), error.round());
        verify(predictionRepo, never()).save(any());
    }

    @Test
    void shouldAllow_whenOpeningCommittedRoundIsFromPreviousRound() {
        prediction.setOpeningCommittedRound(round.getPosition() - 1); // previous round

        stubHappyPath();
        when(predictionRepo.save(any())).thenAnswer(i -> i.getArgument(0));

        var command = new RoundOpeningSwapCommand(List.of(new SwapCommand("ARS", "LIV")));
        var result = useCase.execute(userId, command);

        assertTrue(result.isRight());
    }

    // ── Guard: batch size ─────────────────────────────────────────────────────

    @Test
    void shouldReject_whenBatchIsEmpty() {
        when(seasonRepo.findActiveSeason("premier-league")).thenReturn(Optional.of(season));
        when(roundRepo.findById(season.getCurrentRoundId())).thenReturn(Optional.of(round));
        when(matchRepo.findByRoundId(round.getId())).thenReturn(List.of());
        when(predictionRepo.findByUserAndSeason(userId, seasonId)).thenReturn(Optional.of(prediction));

        var command = new RoundOpeningSwapCommand(List.of());
        var result = useCase.execute(userId, command);

        assertTrue(result.isLeft());
        assertInstanceOf(SwapError.BatchSizeInvalid.class, result.getLeft());
        verify(predictionRepo, never()).save(any());
    }

    @Test
    void shouldReject_whenBatchExceedsTwo() {
        when(seasonRepo.findActiveSeason("premier-league")).thenReturn(Optional.of(season));
        when(roundRepo.findById(season.getCurrentRoundId())).thenReturn(Optional.of(round));
        when(matchRepo.findByRoundId(round.getId())).thenReturn(List.of());
        when(predictionRepo.findByUserAndSeason(userId, seasonId)).thenReturn(Optional.of(prediction));

        var command = new RoundOpeningSwapCommand(List.of(
                new SwapCommand("ARS", "LIV"),
                new SwapCommand("ARS", "LIV"),
                new SwapCommand("ARS", "LIV"))); // 3 swaps
        var result = useCase.execute(userId, command);

        assertTrue(result.isLeft());
        var error = assertInstanceOf(SwapError.BatchSizeInvalid.class, result.getLeft());
        assertEquals(3, error.size());
        verify(predictionRepo, never()).save(any());
    }

    // ── Guard: team validation ────────────────────────────────────────────────

    @Test
    void shouldReject_whenTeamCodeNotInSeason() {
        stubHappyPath();

        var command = new RoundOpeningSwapCommand(List.of(new SwapCommand("XYZ", "LIV")));
        var result = useCase.execute(userId, command);

        assertTrue(result.isLeft());
        var error = assertInstanceOf(SwapError.InvalidTeamCode.class, result.getLeft());
        assertEquals("XYZ", error.code());
        verify(predictionRepo, never()).save(any());
    }

    @Test
    void shouldReject_onFirstInvalidCode_inBatch() {
        stubHappyPath();

        // First swap is valid, second has a bad code
        var command =
                new RoundOpeningSwapCommand(List.of(new SwapCommand("ARS", "LIV"), new SwapCommand("ARS", "BAD")));
        var result = useCase.execute(userId, command);

        assertTrue(result.isLeft());
        assertInstanceOf(SwapError.InvalidTeamCode.class, result.getLeft());
        verify(predictionRepo, never()).save(any());
    }

    // ── Swap mechanics ────────────────────────────────────────────────────────

    @Test
    void shouldRecordEachSwapAsSwapChange() {
        var command = new RoundOpeningSwapCommand(List.of(
                new SwapCommand("ARS", "LIV"),
                new SwapCommand("MCI", "ARS"))); // second swap uses ARS at its new position

        stubHappyPath();
        when(predictionRepo.save(any())).thenAnswer(i -> i.getArgument(0));

        var result = useCase.execute(userId, command);

        assertTrue(result.isRight());
        var saved = result.get().updatedPrediction();
        // 2 swaps in the opening batch → 1 RoundSwap with 2 changes
        assertFalse(saved.getSwaps().isEmpty());
        var roundSwap = saved.getSwaps().stream()
                .filter(rs -> rs.getRound() == round.getPosition())
                .findFirst();
        assertTrue(roundSwap.isPresent());
        assertEquals(2, roundSwap.get().getChanges().size());
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private void stubHappyPath() {
        when(clock.instant()).thenReturn(now);
        when(seasonRepo.findActiveSeason("premier-league")).thenReturn(Optional.of(season));
        when(roundRepo.findById(season.getCurrentRoundId())).thenReturn(Optional.of(round));
        when(matchRepo.findByRoundId(round.getId())).thenReturn(List.of());
        when(predictionRepo.findByUserAndSeason(userId, seasonId)).thenReturn(Optional.of(prediction));
    }

    private Season createSeason() {
        return createSeasonWithRankings(List.of(TeamRank.of("ARS", 1), TeamRank.of("LIV", 2), TeamRank.of("MCI", 3)));
    }

    private Season createSeasonWithRankings(List<TeamRank> rankings) {
        return Season.builder()
                .id(seasonId)
                .currentRoundId(roundId)
                .completed(false)
                .initialRankings(rankings)
                .mainContestId(UUID.randomUUID())
                .build();
    }

    private Round createRound(int position) {
        return Round.builder()
                .id(roundId)
                .seasonId(seasonId)
                .position(position)
                .finalized(false)
                .name("Round " + position)
                .slug("round-" + position)
                .build();
    }

    private SeasonPrediction createPrediction() {
        return createPredictionWithRankings(
                List.of(TeamRank.of("ARS", 1), TeamRank.of("LIV", 2), TeamRank.of("MCI", 3)));
    }

    private SeasonPrediction createPredictionWithRankings(List<TeamRank> rankings) {
        return SeasonPrediction.builder()
                .id(UUID.randomUUID())
                .userId(userId)
                .seasonId(seasonId)
                .initialRankings(rankings)
                .currentRankings(new ArrayList<>(rankings))
                .swaps(new ArrayList<>())
                .lastSwapAt(now.minusSeconds(86400 * 2)) // cooldown long expired
                .openingCommittedRound(0) // opening not yet used this season
                .atRoundNumber(round != null ? round.getPosition() : 5)
                .build();
    }
}
