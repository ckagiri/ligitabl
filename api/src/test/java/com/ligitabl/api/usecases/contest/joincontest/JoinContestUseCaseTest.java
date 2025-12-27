package com.ligitabl.api.usecases.contest.joincontest;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.ligitabl.api.config.CompetitionDefaults;
import com.ligitabl.api.shared.Either;
import com.ligitabl.model.domain.Contest;
import com.ligitabl.model.domain.Round;
import com.ligitabl.model.domain.RoundStatus;
import com.ligitabl.model.domain.Season;
import com.ligitabl.model.domain.SeasonPrediction;
import com.ligitabl.model.domain.TeamRank;
import com.ligitabl.model.repo.ContestRepo;
import com.ligitabl.model.repo.EntryRepo;
import com.ligitabl.model.repo.RoundRepo;
import com.ligitabl.model.repo.SeasonPredictionRepo;
import com.ligitabl.model.repo.SeasonRepo;
import com.ligitabl.model.repo.TeamRepo;

@ExtendWith(MockitoExtension.class)
class JoinContestUseCaseTest {

    private final CompetitionDefaults competitionDefaults = new CompetitionDefaults("premier-league");

    @Mock
    private SeasonRepo seasonRepo;

    @Mock
    private RoundRepo roundRepo;

    @Mock
    private TeamRepo teamRepo;

    @Mock
    private ContestRepo contestRepo;

    @Mock
    private SeasonPredictionRepo predictionRepo;

    @Mock
    private EntryRepo entryRepo;

    @Mock
    private Clock clock;

    private JoinContestUseCase useCase;

    private Instant now;
    private UUID userId;
    private UUID seasonId;
    private UUID roundId;
    private UUID contestId;

    private Season season;
    private Round round;
    private Contest defaultContest;

    @BeforeEach
    void setUp() {
        now = Instant.parse("2024-12-22T10:00:00Z");

        userId = UUID.randomUUID();
        seasonId = UUID.randomUUID();
        roundId = UUID.randomUUID();
        contestId = UUID.randomUUID();

        season = createSeason();
        round = createRound(RoundStatus.OPEN, 1);
        defaultContest = createDefaultContest();

        useCase = new JoinContestUseCase(
                competitionDefaults, seasonRepo, roundRepo, teamRepo, contestRepo, predictionRepo, entryRepo, clock);
    }

    @Test
    void shouldJoinSuccessfully_whenRoundIsOpen() {
        UUID predictionId = UUID.randomUUID();
        UUID entryId = UUID.randomUUID();

        JoinContestCommand request = createValidRequest();

        when(clock.instant()).thenReturn(now);
        when(seasonRepo.findActiveSeason("premier-league")).thenReturn(Optional.of(season));
        when(predictionRepo.findByUserAndSeason(userId, season.getId())).thenReturn(Optional.empty());
        when(roundRepo.findById(season.getCurrentRoundId())).thenReturn(Optional.of(round));
        when(contestRepo.findById(season.getMainContestId())).thenReturn(Optional.of(defaultContest));

        when(predictionRepo.save(any())).thenAnswer(i -> {
            SeasonPrediction p = i.getArgument(0);
            p.setId(predictionId);
            return p;
        });

        when(entryRepo.save(any())).thenAnswer(i -> {
            var e = i.getArgument(0, com.ligitabl.model.domain.Entry.class);
            e.setId(entryId);
            return e;
        });

        Either<JoinContestError, JoinContestResult> result = useCase.execute(userId, request);

        assertTrue(result.isRight());
        JoinContestResult joinResult = result.get();
        assertEquals(predictionId, joinResult.predictionId());
        assertEquals(entryId, joinResult.entryId());
        assertEquals(round.getPosition(), joinResult.atRoundNumber());
        assertTrue(joinResult.message().contains("Round " + round.getPosition()));
    }

    @Test
    void shouldSetNextRound_whenRoundIsLocked() {
        round.setStatus(RoundStatus.LOCKED);

        JoinContestCommand request = createValidRequest();

        when(clock.instant()).thenReturn(now);
        when(seasonRepo.findActiveSeason("premier-league")).thenReturn(Optional.of(season));
        when(predictionRepo.findByUserAndSeason(userId, season.getId())).thenReturn(Optional.empty());
        when(roundRepo.findById(season.getCurrentRoundId())).thenReturn(Optional.of(round));
        when(contestRepo.findById(season.getMainContestId())).thenReturn(Optional.of(defaultContest));
        when(predictionRepo.save(any())).thenAnswer(i -> i.getArgument(0));
        when(entryRepo.save(any())).thenAnswer(i -> i.getArgument(0));

        Either<JoinContestError, JoinContestResult> result = useCase.execute(userId, request);

        assertTrue(result.isRight());
        assertEquals(round.getPosition() + 1, result.get().atRoundNumber());
    }

    @Test
    void shouldReject_whenAlreadyJoined() {
        JoinContestCommand request = createValidRequest();
        SeasonPrediction existingPrediction = SeasonPrediction.builder()
                .id(UUID.randomUUID())
                .userId(userId)
                .seasonId(season.getId())
                .initialRankings(season.getInitialRankings())
                .currentRankings(season.getInitialRankings())
                .atRoundNumber(1)
                .build();

        when(seasonRepo.findActiveSeason("premier-league")).thenReturn(Optional.of(season));
        when(predictionRepo.findByUserAndSeason(userId, season.getId())).thenReturn(Optional.of(existingPrediction));

        Either<JoinContestError, JoinContestResult> result = useCase.execute(userId, request);

        assertTrue(result.isLeft());
        assertInstanceOf(JoinContestError.AlreadyJoined.class, result.getLeft());
        assertEquals(
                existingPrediction.getId(), ((JoinContestError.AlreadyJoined) result.getLeft()).existingPredictionId());
    }

    @Test
    void shouldReject_whenInvalidTeamCount() {
        JoinContestCommand request = new JoinContestCommand(List.of(
                new JoinContestCommand.TeamRankRequest("ARS", 1), new JoinContestCommand.TeamRankRequest("LIV", 2)));

        when(seasonRepo.findActiveSeason("premier-league")).thenReturn(Optional.of(season));
        when(predictionRepo.findByUserAndSeason(userId, season.getId())).thenReturn(Optional.empty());

        Either<JoinContestError, JoinContestResult> result = useCase.execute(userId, request);

        assertTrue(result.isLeft());
        assertInstanceOf(JoinContestError.InvalidTeamCount.class, result.getLeft());
    }

    @Test
    void shouldReject_whenInvalidTeamCodes() {
        JoinContestCommand request = new JoinContestCommand(List.of(
                new JoinContestCommand.TeamRankRequest("XXX", 1),
                new JoinContestCommand.TeamRankRequest("ARS", 2),
                new JoinContestCommand.TeamRankRequest("LIV", 3)));

        when(seasonRepo.findActiveSeason("premier-league")).thenReturn(Optional.of(season));
        when(predictionRepo.findByUserAndSeason(userId, season.getId())).thenReturn(Optional.empty());

        Either<JoinContestError, JoinContestResult> result = useCase.execute(userId, request);

        assertTrue(result.isLeft());
        assertInstanceOf(JoinContestError.InvalidTeamCodes.class, result.getLeft());
    }

    @Test
    void shouldReject_whenLastRoundAndNotOpen() {
        round.setStatus(RoundStatus.LOCKED);
        round.setPosition(3);
        season.setMaxRounds(3);

        JoinContestCommand request = createValidRequest();

        when(seasonRepo.findActiveSeason("premier-league")).thenReturn(Optional.of(season));
        when(predictionRepo.findByUserAndSeason(userId, season.getId())).thenReturn(Optional.empty());
        when(roundRepo.findById(season.getCurrentRoundId())).thenReturn(Optional.of(round));

        Either<JoinContestError, JoinContestResult> result = useCase.execute(userId, request);

        assertTrue(result.isLeft());
        assertInstanceOf(JoinContestError.SeasonEnded.class, result.getLeft());
    }

    private Season createSeason() {
        List<TeamRank> initialRankings = List.of(TeamRank.of("ARS", 1), TeamRank.of("LIV", 2), TeamRank.of("MCI", 3));

        return Season.builder()
                .id(seasonId)
                .totalTeams(3)
                .maxRounds(3)
                .currentRoundId(roundId)
                .mainContestId(contestId)
                .completed(false)
                .initialRankings(initialRankings)
                .build();
    }

    private Round createRound(RoundStatus status, int position) {
        return Round.builder()
                .id(roundId)
                .seasonId(seasonId)
                .position(position)
                .status(status)
                .name("Round " + position)
                .slug("round-" + position)
                .build();
    }

    private Contest createDefaultContest() {
        return Contest.builder()
                .id(contestId)
                .seasonId(seasonId)
                .name("Default")
                .isPrivate(false)
                .fromRoundPosition(1)
                .toRoundPosition(3)
                .maxEntries(100)
                .createdAt(now)
                .build();
    }

    private JoinContestCommand createValidRequest() {
        return new JoinContestCommand(List.of(
                new JoinContestCommand.TeamRankRequest("ARS", 1),
                new JoinContestCommand.TeamRankRequest("LIV", 2),
                new JoinContestCommand.TeamRankRequest("MCI", 3)));
    }
}
