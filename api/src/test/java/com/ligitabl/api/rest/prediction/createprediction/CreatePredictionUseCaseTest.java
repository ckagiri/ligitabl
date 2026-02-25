package com.ligitabl.api.rest.prediction.createprediction;

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
import com.ligitabl.model.domain.Match;
import com.ligitabl.model.domain.MatchStatus;
import com.ligitabl.model.domain.Round;
import com.ligitabl.model.domain.Season;
import com.ligitabl.model.domain.SeasonPrediction;
import com.ligitabl.model.domain.TeamRank;
import com.ligitabl.model.repo.ContestRepo;
import com.ligitabl.model.repo.EntryRepo;
import com.ligitabl.model.repo.MatchRepo;
import com.ligitabl.model.repo.RoundRepo;
import com.ligitabl.model.repo.SeasonPredictionRepo;
import com.ligitabl.model.repo.SeasonRepo;

@ExtendWith(MockitoExtension.class)
class CreatePredictionUseCaseTest {

    private final CompetitionDefaults competitionDefaults = new CompetitionDefaults("premier-league");

    @Mock
    private SeasonRepo seasonRepo;

    @Mock
    private RoundRepo roundRepo;

    @Mock
    private MatchRepo matchRepo;

    @Mock
    private ContestRepo contestRepo;

    @Mock
    private SeasonPredictionRepo predictionRepo;

    @Mock
    private EntryRepo entryRepo;

    @Mock
    private Clock clock;

    private CreatePredictionUseCase useCase;

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
        round = createRound(1, false);
        defaultContest = createDefaultContest();

        useCase = new CreatePredictionUseCase(
                competitionDefaults, seasonRepo, roundRepo, matchRepo, contestRepo, predictionRepo, entryRepo, clock);
    }

    @Test
    void shouldJoinSuccessfully_whenRoundIsOpen() {
        UUID predictionId = UUID.randomUUID();
        UUID entryId = UUID.randomUUID();

        when(clock.instant()).thenReturn(now);
        when(seasonRepo.findActiveSeason("premier-league")).thenReturn(Optional.of(season));
        when(predictionRepo.findByUserAndSeason(userId, season.getId())).thenReturn(Optional.empty());
        when(roundRepo.findById(season.getCurrentRoundId())).thenReturn(Optional.of(round));
        when(matchRepo.findByRoundId(round.getId())).thenReturn(List.of());
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

        Either<CreatePredictionError, CreatePredictionResult> result =
                useCase.execute(userId, new CreatePredictionCommand("LIV", "ARS"));

        assertTrue(result.isRight());
        CreatePredictionResult joinResult = result.get();
        assertEquals(predictionId, joinResult.predictionId());
        assertEquals(entryId, joinResult.entryId());
        assertEquals(round.getPosition(), joinResult.atRoundNumber());
        assertTrue(joinResult.message().contains("Round " + round.getPosition()));
    }

    @Test
    void shouldApplySwapToBaseline_andRecordInitialSwap() {
        when(clock.instant()).thenReturn(now);
        when(seasonRepo.findActiveSeason("premier-league")).thenReturn(Optional.of(season));
        when(predictionRepo.findByUserAndSeason(userId, season.getId())).thenReturn(Optional.empty());
        when(roundRepo.findById(season.getCurrentRoundId())).thenReturn(Optional.of(round));
        when(matchRepo.findByRoundId(round.getId())).thenReturn(List.of());
        when(contestRepo.findById(season.getMainContestId())).thenReturn(Optional.of(defaultContest));
        when(predictionRepo.save(any())).thenAnswer(i -> i.getArgument(0));
        when(entryRepo.save(any())).thenAnswer(i -> i.getArgument(0));

        // Baseline: ARS=1, LIV=2, MCI=3 — swap LIV and ARS → LIV=1, ARS=2, MCI=3
        useCase.execute(userId, new CreatePredictionCommand("LIV", "ARS"));

        verify(predictionRepo).save(argThat(p -> {
            // currentRankings should reflect the swap
            List<TeamRank> rankings = p.getCurrentRankings();
            TeamRank livRank = rankings.stream().filter(t -> t.getCode().equals("LIV")).findFirst().orElseThrow();
            TeamRank arsRank = rankings.stream().filter(t -> t.getCode().equals("ARS")).findFirst().orElseThrow();
            boolean rankingsCorrect = livRank.getPosition() == 1 && arsRank.getPosition() == 2;

            // initialRankings should be empty (deprecated)
            boolean initialEmpty = p.getInitialRankings().isEmpty();

            // lastSwapAt should be null (not a real swap cooldown)
            boolean lastSwapNull = p.getLastSwapAt() == null;

            // swaps should have one entry for atRoundNumber with one SwapChange
            boolean swapRecorded = p.getSwaps().size() == 1
                    && p.getSwaps().get(0).getRound() == round.getPosition()
                    && p.getSwaps().get(0).getChanges().size() == 1;

            return rankingsCorrect && initialEmpty && lastSwapNull && swapRecorded;
        }));
    }

    @Test
    void shouldSetNextRound_whenRoundIsLocked() {
        when(clock.instant()).thenReturn(now);
        when(seasonRepo.findActiveSeason("premier-league")).thenReturn(Optional.of(season));
        when(predictionRepo.findByUserAndSeason(userId, season.getId())).thenReturn(Optional.empty());
        when(roundRepo.findById(season.getCurrentRoundId())).thenReturn(Optional.of(round));
        when(matchRepo.findByRoundId(round.getId()))
                .thenReturn(List.of(Match.builder().status(MatchStatus.LIVE).build()));
        when(contestRepo.findById(season.getMainContestId())).thenReturn(Optional.of(defaultContest));
        when(predictionRepo.save(any())).thenAnswer(i -> i.getArgument(0));
        when(entryRepo.save(any())).thenAnswer(i -> i.getArgument(0));

        Either<CreatePredictionError, CreatePredictionResult> result =
                useCase.execute(userId, new CreatePredictionCommand("LIV", "ARS"));

        assertTrue(result.isRight());
        assertEquals(round.getPosition() + 1, result.get().atRoundNumber());
    }

    @Test
    void shouldReject_whenAlreadyJoined() {
        SeasonPrediction existingPrediction = SeasonPrediction.builder()
                .id(UUID.randomUUID())
                .userId(userId)
                .seasonId(season.getId())
                .initialRankings(List.of())
                .currentRankings(season.getInitialRankings())
                .atRoundNumber(1)
                .build();

        when(seasonRepo.findActiveSeason("premier-league")).thenReturn(Optional.of(season));
        when(predictionRepo.findByUserAndSeason(userId, season.getId())).thenReturn(Optional.of(existingPrediction));

        Either<CreatePredictionError, CreatePredictionResult> result =
                useCase.execute(userId, new CreatePredictionCommand("LIV", "ARS"));

        assertTrue(result.isLeft());
        assertInstanceOf(CreatePredictionError.AlreadyJoined.class, result.getLeft());
        assertEquals(
                existingPrediction.getId(),
                ((CreatePredictionError.AlreadyJoined) result.getLeft()).existingPredictionId());
    }

    @Test
    void shouldReject_whenSameTeam() {
        when(seasonRepo.findActiveSeason("premier-league")).thenReturn(Optional.of(season));
        when(predictionRepo.findByUserAndSeason(userId, season.getId())).thenReturn(Optional.empty());

        Either<CreatePredictionError, CreatePredictionResult> result =
                useCase.execute(userId, new CreatePredictionCommand("ARS", "ARS"));

        assertTrue(result.isLeft());
        assertInstanceOf(CreatePredictionError.SameTeam.class, result.getLeft());
    }

    @Test
    void shouldReject_whenInvalidTeamCode() {
        when(seasonRepo.findActiveSeason("premier-league")).thenReturn(Optional.of(season));
        when(predictionRepo.findByUserAndSeason(userId, season.getId())).thenReturn(Optional.empty());

        Either<CreatePredictionError, CreatePredictionResult> result =
                useCase.execute(userId, new CreatePredictionCommand("XXX", "ARS"));

        assertTrue(result.isLeft());
        assertInstanceOf(CreatePredictionError.InvalidTeamCode.class, result.getLeft());
        assertEquals("XXX", ((CreatePredictionError.InvalidTeamCode) result.getLeft()).code());
    }

    @Test
    void shouldReject_whenLastRoundAndNotOpen() {
        round.setPosition(3);
        season.setMaxRounds(3);

        when(seasonRepo.findActiveSeason("premier-league")).thenReturn(Optional.of(season));
        when(predictionRepo.findByUserAndSeason(userId, season.getId())).thenReturn(Optional.empty());
        when(roundRepo.findById(season.getCurrentRoundId())).thenReturn(Optional.of(round));
        when(matchRepo.findByRoundId(round.getId()))
                .thenReturn(List.of(Match.builder().status(MatchStatus.LIVE).build()));

        Either<CreatePredictionError, CreatePredictionResult> result =
                useCase.execute(userId, new CreatePredictionCommand("LIV", "ARS"));

        assertTrue(result.isLeft());
        assertInstanceOf(CreatePredictionError.Ended.class, result.getLeft());
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

    private Round createRound(int position, boolean finalized) {
        return Round.builder()
                .id(roundId)
                .seasonId(seasonId)
                .position(position)
                .finalized(finalized)
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
}
