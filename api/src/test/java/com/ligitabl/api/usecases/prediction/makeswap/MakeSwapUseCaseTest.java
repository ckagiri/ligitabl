package com.ligitabl.api.usecases.prediction.makeswap;

import com.ligitabl.api.shared.Either;
import com.ligitabl.model.domain.Round;
import com.ligitabl.model.domain.RoundStatus;
import com.ligitabl.model.domain.Season;
import com.ligitabl.model.domain.SeasonPrediction;
import com.ligitabl.model.repo.RoundRepo;
import com.ligitabl.model.repo.SeasonPredictionRepo;
import com.ligitabl.model.repo.SeasonRepo;
import org.junit.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.util.Optional;

import static org.junit.Assert.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.*;

// application/usecase/MakeSwapUseCaseTest.java
@ExtendWith(MockitoExtension.class)
class MakeSwapUseCaseTest {

    @Mock private SeasonPredictionRepo predictionRepo;
    @Mock
    private SeasonRepo seasonRepo;
    @Mock private RoundRepo roundRepo;
    @Mock private Clock clock;

    @InjectMocks
    private MakeSwapUseCase useCase;

    private Season season;
    private Round round;
    private SeasonPrediction prediction;

    @BeforeEach
    void setUp() {
        Instant now = Instant.parse("2024-12-22T10:00:00Z");
        when(clock.instant()).thenReturn(now);

        season = createSeason();
        round = createOpenRound();
        prediction = createPrediction();
    }

    @Test
    void shouldSwapSuccessfully_whenAllConditionsMet() {
        // Arrange
        SwapCommand command = new SwapCommand("ARS", "LIV");

        when(seasonRepo.findActiveSeason()).thenReturn(Optional.of(season));
        when(predictionRepo.findByUserAndSeason(1L, season.getId()))
                .thenReturn(Optional.of(prediction));
        when(roundRepo.findById(season.getCurrentRoundId()))
                .thenReturn(Optional.of(round));
        when(predictionRepo.save(any())).thenAnswer(i -> i.getArgument(0));

        // Act
        Either<SwapError, SwapResult> result = useCase.execute(1L, cmd);

        // Assert
        assertTrue(result.isRight());
        SwapResult swapResult = result.get();
        assertEquals(true, swapResult.success());

        verify(predictionRepo).save(argThat(p ->
                p.getLastSwapAt() != null &&
                        p.getAtRoundNumber() == round.getPosition()
        ));
    }

    @Test
    void shouldRejectSwap_whenCooldownActive() {
        // Arrange
        Instant lastSwap = Instant.parse("2024-12-22T09:00:00Z");
        prediction.setLastSwapAt(lastSwap);

        SwapCommand cmd = new SwapCommand("ARS", "LIV");

        when(seasonRepo.findActiveSeason()).thenReturn(Optional.of(season));
        when(predictionRepo.findByUserAndSeason(1L, season.getId()))
                .thenReturn(Optional.of(prediction));
        when(roundRepo.findById(season.getCurrentRoundId()))
                .thenReturn(Optional.of(round));

        // Act
        Either<SwapError, SwapResult> result = useCase.execute(1L, cmd);

        // Assert
        assertTrue(result.isLeft());
        assertInstanceOf(SwapError.CooldownActive.class, result.getLeft());

        verify(predictionRepo, never()).save(any());
    }

    @Test
    void shouldRejectSwap_whenRoundNotOpen() {
        // Arrange
        round.setStatus(RoundStatus.LOCKED);
        SwapCommand cmd = new SwapCommand("ARS", "LIV");

        when(seasonRepo.findActiveSeason()).thenReturn(Optional.of(season));
        when(predictionRepo.findByUserAndSeason(1L, season.getId()))
                .thenReturn(Optional.of(prediction));
        when(roundRepo.findById(season.getCurrentRoundId()))
                .thenReturn(Optional.of(round));

        // Act
        Either<SwapError, SwapResult> result = useCase.execute(1L, cmd);

        // Assert
        assertTrue(result.isLeft());
        assertInstanceOf(SwapError.RoundNotOpen.class, result.getLeft());
    }

    // Helper methods
    private Season createSeason() {
        return Season.builder()
                .id(1L)
                .totalTeams(20)
                .maxRounds(38)
                .currentRoundId(10L)
                .mainContestId(50L)
                .completed(false)
                .initialRankings(createInitialRankings())
                .build();
    }

    private Round createOpenRound() {
        return Round.builder()
                .id(10L)
                .seasonId(1L)
                .position(10)
                .status(RoundStatus.OPEN)
                .build();
}
