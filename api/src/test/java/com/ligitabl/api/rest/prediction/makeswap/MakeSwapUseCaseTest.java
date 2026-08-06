package com.ligitabl.api.rest.prediction.makeswap;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.*;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.ligitabl.api.testsupport.FixedClockConfig;
import com.ligitabl.api.config.CompetitionDefaults;
import com.ligitabl.api.rest.prediction.shared.SwapHelper;
import com.ligitabl.api.rest.round.shared.RoundSupport;
import com.ligitabl.api.rest.shared.HierarchyValidator;
import com.ligitabl.api.shared.Either;
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
class MakeSwapUseCaseTest {

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
    private HierarchyValidator hierarchyValidator;

    /**
     * A real frozen clock, not a mock. Every stub this replaced returned the same instant and
     * nothing verified interactions on it, so the mock bought nothing — while costing a
     * {@code lenient()} default, because the season-phase predicates consult the clock on paths
     * that return before reaching the rest of the use case. A real clock cannot be unstubbed.
     */
    private final Instant now = FixedClockConfig.NOW;

    private final Clock clock = Clock.fixed(now, ZoneOffset.UTC);

    private MakeSwapUseCase useCase;

    private UUID userId;
    private UUID seasonId;
    private UUID roundId;

    private Season season;
    private Round round;
    private SeasonPrediction prediction;

    @BeforeEach
    void setUp() {

        userId = UUID.randomUUID();
        seasonId = UUID.randomUUID();
        roundId = UUID.randomUUID();

        season = createSeason();
        round = createRound(false, 10);
        prediction = createPrediction();

        useCase = new MakeSwapUseCase(
                roundRepo,
                predictionRepo,
                clock,
                new SwapHelper(
                        competitionDefaults,
                        seasonRepo,
                        roundRepo,
                        predictionRepo,
                        new RoundSupport(roundRepo, matchRepo, hierarchyValidator, competitionDefaults),
                        clock));
    }

    @Test
    void shouldSwapSuccessfully_whenAllConditionsMet() {
        SwapCommand command = new SwapCommand("ARS", "LIV");


        when(seasonRepo.findActiveSeason("premier-league")).thenReturn(Optional.of(season));
        when(predictionRepo.findByUserAndSeason(userId, season.getId())).thenReturn(Optional.of(prediction));
        when(roundRepo.findById(season.getCurrentRoundId())).thenReturn(Optional.of(round));
        when(predictionRepo.save(any())).thenAnswer(i -> i.getArgument(0));

        Either<SwapError, SwapResult> result = useCase.execute(userId, command);

        assertTrue(result.isRight());
        SwapResult swapResult = result.get();
        assertTrue(swapResult.success());
        assertEquals(now.plus(Duration.ofHours(24)), swapResult.nextSwapAt());

        verify(predictionRepo)
                .save(argThat(p -> now.equals(p.getLastSwapAt())
                        && p.getCurrentRankings().stream()
                                .anyMatch(tr -> tr.getCode().equals("ARS") && tr.getPosition() == 2)
                        && p.getCurrentRankings().stream()
                                .anyMatch(tr -> tr.getCode().equals("LIV") && tr.getPosition() == 1)));
    }

    @Test
    void shouldRejectSwap_whenCooldownActive() {
        prediction.setLastSwapAt(now.minus(Duration.ofHours(23)));
        prediction.setOpeningCommittedRound(round.getPosition()); // opening window already used
        SwapCommand command = new SwapCommand("ARS", "LIV");


        when(seasonRepo.findActiveSeason("premier-league")).thenReturn(Optional.of(season));
        when(predictionRepo.findByUserAndSeason(userId, season.getId())).thenReturn(Optional.of(prediction));
        when(roundRepo.findById(season.getCurrentRoundId())).thenReturn(Optional.of(round));

        Either<SwapError, SwapResult> result = useCase.execute(userId, command);

        assertTrue(result.isLeft());
        assertInstanceOf(SwapError.CooldownActive.class, result.getLeft());
        verify(predictionRepo, never()).save(any());
    }

    @Test
    void shouldRejectSwap_whenSeasonInSetupMode() {
        season.enterSetupMode();
        SwapCommand command = new SwapCommand("ARS", "LIV");

        when(seasonRepo.findActiveSeason("premier-league")).thenReturn(Optional.of(season));

        Either<SwapError, SwapResult> result = useCase.execute(userId, command);

        assertTrue(result.isLeft());
        assertInstanceOf(SwapError.SeasonInSetupMode.class, result.getLeft());
        verify(predictionRepo, never()).save(any());
    }

    @Test
    void shouldRejectSwap_whenRoundNotOpen() {
        SwapCommand command = new SwapCommand("ARS", "LIV");

        when(seasonRepo.findActiveSeason("premier-league")).thenReturn(Optional.of(season));
        when(predictionRepo.findByUserAndSeason(userId, season.getId())).thenReturn(Optional.of(prediction));
        when(roundRepo.findById(season.getCurrentRoundId())).thenReturn(Optional.of(round));
        when(matchRepo.findByRoundId(round.getId()))
                .thenReturn(List.of(Match.builder().status(MatchStatus.LIVE).build()));

        Either<SwapError, SwapResult> result = useCase.execute(userId, command);

        assertTrue(result.isLeft());
        assertInstanceOf(SwapError.RoundNotOpen.class, result.getLeft());
        verify(predictionRepo, never()).save(any());
    }

    @Test
    void shouldRejectSwap_whenLastRoundFinalized() {
        season.setMaxRounds(10);
        round = createRound(true, 10);
        season.setCurrentRoundId(round.getId());
        SwapCommand command = new SwapCommand("ARS", "LIV");

        when(seasonRepo.findActiveSeason("premier-league")).thenReturn(Optional.of(season));
        when(predictionRepo.findByUserAndSeason(userId, season.getId())).thenReturn(Optional.of(prediction));
        when(roundRepo.findById(season.getCurrentRoundId())).thenReturn(Optional.of(round));

        Either<SwapError, SwapResult> result = useCase.execute(userId, command);

        assertTrue(result.isLeft());
        assertInstanceOf(SwapError.RoundNotOpen.class, result.getLeft());
        verify(predictionRepo, never()).save(any());
    }

    @Test
    void shouldRejectSwap_whenLastRoundAdvanced() {
        season.setMaxRounds(10);
        round = createRound(true, 10);
        round.setAdvanced(true);
        season.setCurrentRoundId(round.getId());
        SwapCommand command = new SwapCommand("ARS", "LIV");

        when(seasonRepo.findActiveSeason("premier-league")).thenReturn(Optional.of(season));
        when(predictionRepo.findByUserAndSeason(userId, season.getId())).thenReturn(Optional.of(prediction));
        when(roundRepo.findById(season.getCurrentRoundId())).thenReturn(Optional.of(round));

        Either<SwapError, SwapResult> result = useCase.execute(userId, command);

        assertTrue(result.isLeft());
        assertInstanceOf(SwapError.RoundNotOpen.class, result.getLeft());
        verify(predictionRepo, never()).save(any());
    }

    @Test
    void shouldUpdateAtRoundNumber_whenSwapApplied() {
        prediction.setAtRoundNumber(5); // stale from an earlier round, distinct from the target round
        SwapCommand command = new SwapCommand("ARS", "LIV");


        when(seasonRepo.findActiveSeason("premier-league")).thenReturn(Optional.of(season));
        when(predictionRepo.findByUserAndSeason(userId, season.getId())).thenReturn(Optional.of(prediction));
        when(roundRepo.findById(season.getCurrentRoundId())).thenReturn(Optional.of(round));
        when(predictionRepo.save(any())).thenAnswer(i -> i.getArgument(0));

        Either<SwapError, SwapResult> result = useCase.execute(userId, command);

        assertTrue(result.isRight());
        verify(predictionRepo).save(argThat(p -> p.getAtRoundNumber() == round.getPosition()));
    }

    @Test
    void shouldValidateNextRound_whenPredictionAlreadyOnNextRound() {
        SwapCommand command = new SwapCommand("ARS", "LIV");
        Round nextRound = Round.builder()
                .id(UUID.randomUUID())
                .seasonId(seasonId)
                .position(11)
                .finalized(false)
                .name("Round 11")
                .slug("round-11")
                .build();
        prediction.setAtRoundNumber(11);
        round = createRound(true, 10);
        season.setCurrentRoundId(round.getId());

        when(seasonRepo.findActiveSeason("premier-league")).thenReturn(Optional.of(season));
        when(predictionRepo.findByUserAndSeason(userId, season.getId())).thenReturn(Optional.of(prediction));
        when(roundRepo.findById(season.getCurrentRoundId())).thenReturn(Optional.of(round));
        when(roundRepo.findBySeasonIdAndPosition(season.getId(), 11)).thenReturn(Optional.of(nextRound));
        when(matchRepo.findByRoundId(nextRound.getId())).thenReturn(List.of());
        when(predictionRepo.save(any())).thenAnswer(i -> i.getArgument(0));

        Either<SwapError, SwapResult> result = useCase.execute(userId, command);

        assertTrue(result.isRight());
        verify(matchRepo, never()).findByRoundId(round.getId());
        verify(matchRepo).findByRoundId(nextRound.getId());
        verify(predictionRepo).save(argThat(p -> p.getAtRoundNumber() == nextRound.getPosition()));
    }

    private Season createSeason() {
        return Season.builder()
                .id(seasonId)
                .currentRoundId(roundId)
                .completed(false)
                .mainContestId(UUID.randomUUID())
                .build();
    }

    private Round createRound(boolean finalized, int position) {
        return Round.builder()
                .id(roundId)
                .seasonId(seasonId)
                .position(position)
                .finalized(finalized)
                .name("Round " + position)
                .slug("round-" + position)
                .build();
    }

    private SeasonPrediction createPrediction() {
        List<TeamRank> rankings = List.of(TeamRank.of("ARS", 1), TeamRank.of("LIV", 2), TeamRank.of("MCI", 3));

        return SeasonPrediction.builder()
                .id(UUID.randomUUID())
                .userId(userId)
                .seasonId(seasonId)
                .initialRankings(rankings)
                .currentRankings(rankings)
                .swaps(new ArrayList<>())
                .lastSwapAt(null)
                .atRoundNumber(10)
                .build();
    }
}
