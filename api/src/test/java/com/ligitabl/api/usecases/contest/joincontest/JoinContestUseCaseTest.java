package com.ligitabl.api.usecases.contest.joincontest;

import com.ligitabl.api.shared.Either;
import com.ligitabl.model.domain.*;
import com.ligitabl.model.repo.*;
import org.junit.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.*;

// application/usecase/JoinContestUseCaseTest.java
@ExtendWith(MockitoExtension.class)
@Transactional
class JoinContestUseCaseTest {

    @Mock
    private SeasonRepo seasonRepo;
    @Mock private RoundRepo roundRepo;
    @Mock private TeamRepo teamRepo;
    @Mock private ContestRepo contestRepo;
    @Mock private SeasonPredictionRepo predictionRepo;
    @Mock private EntryRepo entryRepo;
    @Mock private Clock clock;

    @InjectMocks
    private JoinContestUseCase useCase;

    private Season season;
    private Round openRound;
    private Contest defaultContest;
    private Instant now;

    @BeforeEach
    void setUp() {
        now = Instant.parse("2024-12-22T10:00:00Z");
        when(clock.instant()).thenReturn(now);

        season = createSeason();
        openRound = createOpenRound();
        defaultContest = createDefaultContest();
    }

    @Test
    void shouldJoinSuccessfully_whenRoundIsOpen() {
        // Arrange
       JoinContestCommand command = createValidRequest();

        when(seasonRepo.findActiveSeason()).thenReturn(Optional.of(season));
        when(predictionRepo.findByUserAndSeason(1L, season.getId()))
                .thenReturn(Optional.empty());
        when(roundRepo.findById(season.getCurrentRoundId()))
                .thenReturn(Optional.of(openRound));
        when(contestRepo.findById(season.getMainContestId()))
                .thenReturn(Optional.of(defaultContest));
        when(predictionRepo.save(any())).thenAnswer(i -> {
            SeasonPrediction p = i.getArgument(0);
            p.setId(100L);
            return p;
        });
        when(entryRepo.save(any())).thenAnswer(i -> {
            Entry e = i.getArgument(0);
            e.setId(200L);
            return e;
        });

        // Act
        Either<JoinContestError, JoinContestResult> result = useCase.execute(1L, request);

        // Assert
        assertTrue(result.isRight());
        JoinContestResult joinResult = result.get();

        assertEquals(100L, joinResult.predictionId());
        assertEquals(200L, joinResult.entryId());
        assertEquals(openRound.getPosition(), joinResult.atRoundNumber());

        verify(predictionRepo).save(argThat(p ->
                p.getUserId().equals(1L) &&
                        p.getSeasonId().equals(season.getId()) &&
                        p.getAtRoundNumber() == openRound.getPosition() &&
                        p.getLastSwapAt() == null &&
                        p.getSwaps().isEmpty() &&
                        p.getInitialRankings().equals(p.getCurrentRankings())
        ));

        verify(entryRepo).save(argThat(e ->
                e.getUserId().equals(1L) &&
                        e.getContestId().equals(defaultContest.getId())
        ));
    }

    @Test
    void shouldSetNextRound_whenRoundIsLocked() {
        // Arrange
        openRound.setStatus(RoundStatus.LOCKED);
        JoinContestCommand request = createValidRequest();

        when(seasonRepo.findActiveSeason()).thenReturn(Optional.of(season));
        when(predictionRepo.findByUserAndSeason(1L, season.getId()))
                .thenReturn(Optional.empty());
        when(roundRepo.findById(season.getCurrentRoundId()))
                .thenReturn(Optional.of(openRound));
        when(contestRepo.findById(season.getMainContestId()))
                .thenReturn(Optional.of(defaultContest));
        when(predictionRepo.save(any())).thenAnswer(i -> {
            SeasonPrediction p = i.getArgument(0);
            p.setId(100L);
            return p;
        });
        when(entryRepo.save(any())).thenAnswer(i -> {
            Entry e = i.getArgument(0);
            e.setId(200L);
            return e;
        });

        // Act
        Either<JoinContestError, JoinContestResult> result = useCase.execute(1L, request);

        // Assert
        assertTrue(result.isRight());
        JoinContestResult joinResult = result.get();

        assertEquals(openRound.getPosition() + 1, joinResult.atRoundNumber());
        assertTrue(joinResult.message().contains("Round " + (openRound.getPosition() + 1)));
    }

    @Test
    void shouldReject_whenAlreadyJoined() {
        // Arrange
        JoinContestCommand request = createValidRequest();
        SeasonPrediction existingPrediction = SeasonPrediction.builder()
                .id(999L)
                .userId(1L)
                .seasonId(season.getId())
                .build();

        when(seasonRepo.findActiveSeason()).thenReturn(Optional.of(season));
        when(predictionRepo.findByUserAndSeason(1L, season.getId()))
                .thenReturn(Optional.of(existingPrediction));

        // Act
        Either<JoinContestError, JoinContestResult> result = useCase.execute(1L, request);

        // Assert
        assertTrue(result.isLeft());
        assertInstanceOf(JoinContestError.AlreadyJoined.class, result.getLeft());
        assertEquals(999L, ((JoinContestError.AlreadyJoined) result.getLeft()).existingPredictionId());

        verify(predictionRepo, never()).save(any());
        verify(entryRepo, never()).save(any());
    }

    @Test
    void shouldReject_whenSeasonCompleted() {
        // Arrange
        season.setCompleted(true);
        JoinContestCommand request = createValidRequest();

        when(seasonRepo.findActiveSeason()).thenReturn(Optional.of(season));

        // Act
        Either<JoinContestError, JoinContestResult> result = useCase.execute(1L, request);

        // Assert
        assertTrue(result.isLeft());
        assertInstanceOf(JoinContestError.SeasonCompleted.class, result.getLeft());
    }

    @Test
    void shouldReject_whenInvalidTeamCount() {
        // Arrange
        JoinContestCommand request = new JoinContestCommand(
                List.of(
                        new JoinContestCommand.TeamRankRequest("ARS", 1),
                        new JoinContestCommand.TeamRankRequest("LIV", 2)
                        // Only 2 teams instead of 20
                )
        );

        when(seasonRepo.findActiveSeason()).thenReturn(Optional.of(season));
        when(predictionRepo.findByUserAndSeason(1L, season.getId()))
                .thenReturn(Optional.empty());

        // Act
        Either<JoinContestError, JoinContestResult> result = useCase.execute(1L, request);

        // Assert
        assertTrue(result.isLeft());
        assertInstanceOf(JoinContestError.InvalidTeamCount.class, result.getLeft());

        var error = (JoinContestError.InvalidTeamCount) result.getLeft();
        assertEquals(2, error.provided());
        assertEquals(20, error.required());
    }

    @Test
    void shouldReject_whenDuplicatePositions() {
        // Arrange
        JoinContestCommand request = createRequestWithDuplicatePositions();

        when(seasonRepo.findActiveSeason()).thenReturn(Optional.of(season));
        when(predictionRepo.findByUserAndSeason(1L, season.getId()))
                .thenReturn(Optional.empty());

        // Act
        Either<JoinContestError, JoinContestResult> result = useCase.execute(1L, request);

        // Assert
        assertTrue(result.isLeft());
        assertInstanceOf(JoinContestError.DuplicatePositions.class, result.getLeft());
    }

    @Test
    void shouldReject_whenInvalidTeamCodes() {
        // Arrange
        JoinContestCommand request = createRequestWithInvalidCode();

        when(seasonRepo.findActiveSeason()).thenReturn(Optional.of(season));
        when(predictionRepo.findByUserAndSeason(1L, season.getId()))
                .thenReturn(Optional.empty());

        // Act
        Either<JoinContestError, JoinContestResult> result = useCase.execute(1L, request);

        // Assert
        assertTrue(result.isLeft());
        assertInstanceOf(JoinContestError.InvalidTeamCodes.class, result.getLeft());

        var error = (JoinContestError.InvalidTeamCodes) result.getLeft();
        assertTrue(error.invalidCodes().contains("XXX"));
    }

    @Test
    void shouldReject_whenLastRoundAndNotOpen() {
        // Arrange
        openRound.setPosition(38); // Last round
        openRound.setStatus(RoundStatus.LOCKED);
        season.setMaxRounds(38);

        JoinContestCommand request = createValidRequest();

        when(seasonRepo.findActiveSeason()).thenReturn(Optional.of(season));
        when(predictionRepo.findByUserAndSeason(1L, season.getId()))
                .thenReturn(Optional.empty());
        when(roundRepo.findById(season.getCurrentRoundId()))
                .thenReturn(Optional.of(openRound));

        // Act
        Either<JoinContestError, JoinContestResult> result = useCase.execute(1L, request);

        // Assert
        assertTrue(result.isLeft());
        assertInstanceOf(JoinContestError.SeasonEnded.class, result.getLeft());
    }

    // Helper methods
    private Season createSeason() {
        return Season.builder()
                .id(UUID.randomUUID())
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

    private Contest createDefaultContest() {
        return Contest.builder()
                .id(50L)
                .seasonId(1L)
                .name("Premier League 24/25")
                .isPrivate(false)
                .fromRound(1)
                .toRound(38)
                .maxEntries(100)
                .createdAt(now)
                .build();
    }

    private List<TeamRank> createInitialRankings() {
        List<TeamRank> rankings = new ArrayList<>();
        String[] codes = {"MCI", "ARS", "LIV", "AVL", "TOT", "CHE", "NEW", "MUN",
                "WHU", "BHA", "WOL", "FUL", "BOU", "CRY", "BRE", "EVE",
                "NFO", "LEI", "IPS", "SOU"};
        for (int i = 0; i < codes.length; i++) {
            rankings.add(new TeamRank(codes[i], i + 1));
        }
        return rankings;
    }

    private JoinContestCommand createValidRequest() {
        return new JoinContestCommand(
                createInitialRankings().stream()
                        .map(tr -> new JoinContestCommand.TeamRankRequest(tr.getCode(), tr.getPosition()))
                        .toList()
        );
    }

    private JoinContestCommand createRequestWithDuplicatePositions() {
        List<JoinContestCommand.TeamRankRequest> rankings = new ArrayList<>();
        rankings.add(new JoinContestCommand.TeamRankRequest("ARS", 1));
        rankings.add(new JoinContestCommand.TeamRankRequest("LIV", 1)); // Duplicate position
        for (int i = 2; i <= 20; i++) {
            rankings.add(new JoinContestCommand.TeamRankRequest("TEAM" + i, i));
        }
        return new JoinContestCommand(rankings);
    }

    private JoinContestCommand createRequestWithInvalidCode() {
        List<JoinContestCommand.TeamRankRequest> rankings = new ArrayList<>();
        rankings.add(new JoinContestCommand.TeamRankRequest("XXX", 1)); // Invalid code
        for (int i = 1; i < 20; i++) {
            rankings.add(new JoinContestCommand.TeamRankRequest(
                    createInitialRankings().get(i).getCode(),
                    i + 1
            ));
        }
        return new JoinContestCommand(rankings);
    }
}
