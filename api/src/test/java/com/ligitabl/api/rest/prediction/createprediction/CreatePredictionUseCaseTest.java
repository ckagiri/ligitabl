package com.ligitabl.api.rest.prediction.createprediction;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.AdditionalAnswers.returnsFirstArg;
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
import com.ligitabl.api.rest.prediction.shared.SwapHelper;
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
import com.ligitabl.model.repo.StandingsRepo;

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
    private StandingsRepo standingsRepo;

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
                competitionDefaults,
                seasonRepo,
                roundRepo,
                matchRepo,
                contestRepo,
                predictionRepo,
                entryRepo,
                standingsRepo,
                new SwapHelper(competitionDefaults, seasonRepo, roundRepo, predictionRepo, matchRepo),
                clock);
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
                useCase.execute(userId, singleSwap("LIV", "ARS"));

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
        useCase.execute(userId, singleSwap("LIV", "ARS"));

        verify(predictionRepo).save(argThat(p -> {
            // currentRankings should reflect the swap
            List<TeamRank> rankings = p.getCurrentRankings();
            TeamRank livRank = rankings.stream()
                    .filter(t -> t.getCode().equals("LIV"))
                    .findFirst()
                    .orElseThrow();
            TeamRank arsRank = rankings.stream()
                    .filter(t -> t.getCode().equals("ARS"))
                    .findFirst()
                    .orElseThrow();
            boolean rankingsCorrect = livRank.getPosition() == 1 && arsRank.getPosition() == 2;

            // initialRankings should be null for a normal (non pre-season) join
            boolean initialNull = p.getInitialRankings() == null;

            // lastSwapAt should be null (not a real swap cooldown)
            boolean lastSwapNull = p.getLastSwapAt() == null;

            // swaps should have one entry for atRoundNumber with one SwapChange
            boolean swapRecorded = p.getSwaps().size() == 1
                    && p.getSwaps().get(0).getRound() == round.getPosition()
                    && p.getSwaps().get(0).getChanges().size() == 1;

            return rankingsCorrect && initialNull && lastSwapNull && swapRecorded;
        }));
    }

    @Test
    void shouldApplyMultipleSwapsToBaseline_andRecordAllChanges() {
        when(clock.instant()).thenReturn(now);
        when(seasonRepo.findActiveSeason("premier-league")).thenReturn(Optional.of(season));
        when(predictionRepo.findByUserAndSeason(userId, season.getId())).thenReturn(Optional.empty());
        when(roundRepo.findById(season.getCurrentRoundId())).thenReturn(Optional.of(round));
        when(matchRepo.findByRoundId(round.getId())).thenReturn(List.of());
        when(contestRepo.findById(season.getMainContestId())).thenReturn(Optional.of(defaultContest));
        when(predictionRepo.save(any())).thenAnswer(i -> i.getArgument(0));
        when(entryRepo.save(any())).thenAnswer(i -> i.getArgument(0));

        // Baseline: ARS=1, LIV=2, MCI=3
        // Swap 1: LIV ↔ ARS → LIV=1, ARS=2, MCI=3
        // Swap 2: MCI ↔ ARS → LIV=1, MCI=2, ARS=3
        useCase.execute(
                userId,
                multiSwap(List.of(
                        new CreatePredictionCommand.SwapPair("LIV", "ARS"),
                        new CreatePredictionCommand.SwapPair("MCI", "ARS"))));

        verify(predictionRepo).save(argThat(p -> {
            List<TeamRank> rankings = p.getCurrentRankings();
            TeamRank liv = rankings.stream()
                    .filter(t -> t.getCode().equals("LIV"))
                    .findFirst()
                    .orElseThrow();
            TeamRank mci = rankings.stream()
                    .filter(t -> t.getCode().equals("MCI"))
                    .findFirst()
                    .orElseThrow();
            TeamRank ars = rankings.stream()
                    .filter(t -> t.getCode().equals("ARS"))
                    .findFirst()
                    .orElseThrow();
            boolean rankingsCorrect = liv.getPosition() == 1 && mci.getPosition() == 2 && ars.getPosition() == 3;

            // 2 swap changes recorded under one RoundSwap
            boolean swapsRecorded = p.getSwaps().size() == 1
                    && p.getSwaps().get(0).getRound() == round.getPosition()
                    && p.getSwaps().get(0).getChanges().size() == 2;

            boolean lastSwapNull = p.getLastSwapAt() == null;

            return rankingsCorrect && swapsRecorded && lastSwapNull;
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
                useCase.execute(userId, singleSwap("LIV", "ARS"));

        assertTrue(result.isRight());
        assertEquals(round.getPosition() + 1, result.get().atRoundNumber());
    }

    @Test
    void shouldReject_whenAlreadyJoined() {
        SeasonPrediction existingPrediction = SeasonPrediction.builder()
                .id(UUID.randomUUID())
                .userId(userId)
                .seasonId(season.getId())
                .currentRankings(season.getInitialRankings())
                .atRoundNumber(1)
                .build();

        when(seasonRepo.findActiveSeason("premier-league")).thenReturn(Optional.of(season));
        when(predictionRepo.findByUserAndSeason(userId, season.getId())).thenReturn(Optional.of(existingPrediction));

        Either<CreatePredictionError, CreatePredictionResult> result =
                useCase.execute(userId, singleSwap("LIV", "ARS"));

        assertTrue(result.isLeft());
        assertInstanceOf(CreatePredictionError.AlreadyJoined.class, result.getLeft());
        assertEquals(
                existingPrediction.getId(),
                ((CreatePredictionError.AlreadyJoined) result.getLeft()).existingPredictionId());
    }

    @Test
    void shouldAllow_whenEmptySwapList() {
        when(clock.instant()).thenReturn(now);
        when(seasonRepo.findActiveSeason("premier-league")).thenReturn(Optional.of(season));
        when(predictionRepo.findByUserAndSeason(userId, season.getId())).thenReturn(Optional.empty());
        when(roundRepo.findById(season.getCurrentRoundId())).thenReturn(Optional.of(round));
        when(matchRepo.findByRoundId(round.getId())).thenReturn(List.of());
        when(contestRepo.findById(season.getMainContestId())).thenReturn(Optional.of(defaultContest));
        when(predictionRepo.save(any())).thenAnswer(returnsFirstArg());
        when(entryRepo.save(any())).thenAnswer(returnsFirstArg());

        Either<CreatePredictionError, CreatePredictionResult> result = useCase.execute(userId, multiSwap(List.of()));

        assertTrue(result.isRight());
    }

    @Test
    void shouldAllow_whenFiveSwapsProvided() {
        when(seasonRepo.findActiveSeason("premier-league")).thenReturn(Optional.of(season));
        when(predictionRepo.findByUserAndSeason(userId, season.getId())).thenReturn(Optional.empty());
        when(roundRepo.findById(season.getCurrentRoundId())).thenReturn(Optional.of(round));
        when(matchRepo.findByRoundId(round.getId())).thenReturn(List.of());
        when(contestRepo.findById(season.getMainContestId())).thenReturn(Optional.of(defaultContest));

        when(predictionRepo.save(any())).thenAnswer(returnsFirstArg());
        when(entryRepo.save(any())).thenAnswer(returnsFirstArg());

        Either<CreatePredictionError, CreatePredictionResult> result = useCase.execute(
                userId,
                multiSwap(List.of(
                        new CreatePredictionCommand.SwapPair("LIV", "ARS"),
                        new CreatePredictionCommand.SwapPair("MCI", "ARS"),
                        new CreatePredictionCommand.SwapPair("LIV", "MCI"),
                        new CreatePredictionCommand.SwapPair("ARS", "MCI"),
                        new CreatePredictionCommand.SwapPair("MCI", "LIV"))));

        assertTrue(result.isRight());
    }

    @Test
    void shouldReject_whenTooManySwaps() {
        when(seasonRepo.findActiveSeason("premier-league")).thenReturn(Optional.of(season));
        when(predictionRepo.findByUserAndSeason(userId, season.getId())).thenReturn(Optional.empty());

        Either<CreatePredictionError, CreatePredictionResult> result = useCase.execute(
                userId,
                multiSwap(List.of(
                        new CreatePredictionCommand.SwapPair("LIV", "ARS"),
                        new CreatePredictionCommand.SwapPair("MCI", "ARS"),
                        new CreatePredictionCommand.SwapPair("LIV", "MCI"),
                        new CreatePredictionCommand.SwapPair("ARS", "MCI"),
                        new CreatePredictionCommand.SwapPair("CHE", "TOT"),
                        new CreatePredictionCommand.SwapPair("AVL", "NEW"))));

        assertTrue(result.isLeft());
        assertInstanceOf(CreatePredictionError.TooManySwaps.class, result.getLeft());
        assertEquals(6, ((CreatePredictionError.TooManySwaps) result.getLeft()).provided());
        assertEquals(5, ((CreatePredictionError.TooManySwaps) result.getLeft()).max());
    }

    @Test
    void shouldReject_whenSameTeam() {
        when(seasonRepo.findActiveSeason("premier-league")).thenReturn(Optional.of(season));
        when(predictionRepo.findByUserAndSeason(userId, season.getId())).thenReturn(Optional.empty());

        Either<CreatePredictionError, CreatePredictionResult> result =
                useCase.execute(userId, singleSwap("ARS", "ARS"));

        assertTrue(result.isLeft());
        assertInstanceOf(CreatePredictionError.SameTeam.class, result.getLeft());
    }

    @Test
    void shouldReject_whenInvalidTeamCode() {
        when(seasonRepo.findActiveSeason("premier-league")).thenReturn(Optional.of(season));
        when(predictionRepo.findByUserAndSeason(userId, season.getId())).thenReturn(Optional.empty());

        Either<CreatePredictionError, CreatePredictionResult> result =
                useCase.execute(userId, singleSwap("XXX", "ARS"));

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
        when(contestRepo.findById(season.getMainContestId())).thenReturn(Optional.of(defaultContest));
        when(roundRepo.findById(season.getCurrentRoundId())).thenReturn(Optional.of(round));
        when(matchRepo.findByRoundId(round.getId()))
                .thenReturn(List.of(Match.builder().status(MatchStatus.LIVE).build()));

        Either<CreatePredictionError, CreatePredictionResult> result =
                useCase.execute(userId, singleSwap("LIV", "ARS"));

        assertTrue(result.isLeft());
        assertInstanceOf(CreatePredictionError.Ended.class, result.getLeft());
    }

    @Test
    void shouldRegisterPreSeason_whenSeasonIsPreSeason() {
        season.setPredictionsOpenAt(java.time.OffsetDateTime.now().plusDays(30));
        // isPreSeason() == true: !completed && predictionsOpenAt in the future

        UUID predictionId = UUID.randomUUID();
        UUID entryId = UUID.randomUUID();

        when(clock.instant()).thenReturn(now);
        when(seasonRepo.findActiveSeason("premier-league")).thenReturn(Optional.of(season));
        when(predictionRepo.findByUserAndSeason(userId, season.getId())).thenReturn(Optional.empty());
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
                useCase.execute(userId, singleSwap("LIV", "ARS"));

        assertTrue(result.isRight());
        assertEquals(predictionId, result.get().predictionId());
        assertEquals(entryId, result.get().entryId());
        assertEquals(0, result.get().atRoundNumber());
        verify(roundRepo, never()).findById(any());

        verify(predictionRepo)
                .save(argThat(p -> p.getAtRoundNumber() == 0
                        && p.getInitialRankings() != null
                        && p.getInitialRankings().equals(p.getCurrentRankings())
                        && p.getSwaps().size() == 1
                        && p.getSwaps().get(0).getRound() == 0
                        && p.getSwaps().get(0).getChanges().size() == 1));
    }

    @Test
    void shouldReject_whenAlreadyPreRegistered_andStillPreSeason() {
        season.setPredictionsOpenAt(java.time.OffsetDateTime.now().plusDays(30));
        SeasonPrediction preSeasonPrediction = SeasonPrediction.builder()
                .id(UUID.randomUUID())
                .userId(userId)
                .seasonId(season.getId())
                .initialRankings(season.getInitialRankings())
                .currentRankings(season.getInitialRankings())
                .atRoundNumber(0)
                .build();

        when(seasonRepo.findActiveSeason("premier-league")).thenReturn(Optional.of(season));
        when(predictionRepo.findByUserAndSeason(userId, season.getId())).thenReturn(Optional.of(preSeasonPrediction));

        Either<CreatePredictionError, CreatePredictionResult> result =
                useCase.execute(userId, singleSwap("LIV", "ARS"));

        assertTrue(result.isLeft());
        assertInstanceOf(CreatePredictionError.AlreadyJoined.class, result.getLeft());
    }

    @Test
    void shouldMergePreSeasonRegistration_whenPredictionsNowOpen_updatesInPlace() {
        // season.predictionsOpenAt is null on the default fixture => isPredictionsOpen()==true => isPreSeason()==false
        UUID existingId = UUID.randomUUID();
        UUID existingEntryId = UUID.randomUUID();
        List<TeamRank> preSeasonRankings = List.of(TeamRank.of("LIV", 1), TeamRank.of("ARS", 2), TeamRank.of("MCI", 3));
        SeasonPrediction existing = SeasonPrediction.builder()
                .id(existingId)
                .userId(userId)
                .seasonId(season.getId())
                .initialRankings(preSeasonRankings)
                .currentRankings(preSeasonRankings)
                .swaps(new java.util.ArrayList<>(
                        List.of(new com.ligitabl.model.domain.RoundSwap(0, new java.util.ArrayList<>()))))
                .atRoundNumber(0)
                .build();

        com.ligitabl.model.domain.Entry existingEntry = com.ligitabl.model.domain.Entry.builder()
                .id(existingEntryId)
                .userId(userId)
                .contestId(contestId)
                .joinedAtRound(0)
                .build();

        when(clock.instant()).thenReturn(now);
        when(seasonRepo.findActiveSeason("premier-league")).thenReturn(Optional.of(season));
        when(predictionRepo.findByUserAndSeason(userId, season.getId())).thenReturn(Optional.of(existing));
        when(roundRepo.findById(season.getCurrentRoundId())).thenReturn(Optional.of(round));
        when(matchRepo.findByRoundId(round.getId())).thenReturn(List.of());
        when(contestRepo.findById(season.getMainContestId())).thenReturn(Optional.of(defaultContest));
        when(entryRepo.findByUserAndContest(userId, contestId)).thenReturn(Optional.of(existingEntry));
        when(predictionRepo.save(any())).thenAnswer(i -> i.getArgument(0));

        Either<CreatePredictionError, CreatePredictionResult> result = useCase.execute(userId, multiSwap(List.of()));

        assertTrue(result.isRight());
        assertEquals(round.getPosition(), result.get().atRoundNumber());
        assertEquals(existingEntryId, result.get().entryId());
        assertEquals(existingId, result.get().predictionId());

        verify(entryRepo, never()).save(any());
        verify(predictionRepo)
                .save(argThat(p -> p.getId().equals(existingId)
                        && p.getAtRoundNumber() == round.getPosition()
                        && p.getInitialRankings() != null
                        && p.getInitialRankings().equals(preSeasonRankings)));
    }

    @Test
    void shouldReject_whenMergingPreSeasonRegistration_withNullInitialRankings() {
        // predictions now open (season has null predictionsOpenAt → isPredictionsOpen() == true)
        UUID existingId = UUID.randomUUID();
        SeasonPrediction corrupt = SeasonPrediction.builder()
                .id(existingId)
                .userId(userId)
                .seasonId(season.getId())
                .initialRankings(null) // corrupt: no pre-reg marker
                .currentRankings(season.getInitialRankings())
                .atRoundNumber(0)
                .build();

        when(seasonRepo.findActiveSeason("premier-league")).thenReturn(Optional.of(season));
        when(predictionRepo.findByUserAndSeason(userId, season.getId())).thenReturn(Optional.of(corrupt));
        when(contestRepo.findById(season.getMainContestId())).thenReturn(Optional.of(defaultContest));
        when(entryRepo.findByUserAndContest(userId, contestId))
                .thenReturn(Optional.of(com.ligitabl.model.domain.Entry.builder()
                        .id(UUID.randomUUID())
                        .userId(userId)
                        .contestId(contestId)
                        .joinedAtRound(0)
                        .build()));
        when(roundRepo.findById(season.getCurrentRoundId())).thenReturn(Optional.of(round));
        when(matchRepo.findByRoundId(round.getId())).thenReturn(List.of());

        Either<CreatePredictionError, CreatePredictionResult> result = useCase.execute(userId, multiSwap(List.of()));

        assertTrue(result.isLeft());
        assertInstanceOf(CreatePredictionError.CorruptPreSeasonRegistration.class, result.getLeft());
        assertEquals(
                existingId, ((CreatePredictionError.CorruptPreSeasonRegistration) result.getLeft()).predictionId());
    }

    @Test
    void shouldReject_whenMergingPreSeasonRegistration_withEmptyInitialRankings() {
        UUID existingId = UUID.randomUUID();
        SeasonPrediction corrupt = SeasonPrediction.builder()
                .id(existingId)
                .userId(userId)
                .seasonId(season.getId())
                .initialRankings(List.of()) // empty is also corrupt
                .currentRankings(season.getInitialRankings())
                .atRoundNumber(0)
                .build();

        when(seasonRepo.findActiveSeason("premier-league")).thenReturn(Optional.of(season));
        when(predictionRepo.findByUserAndSeason(userId, season.getId())).thenReturn(Optional.of(corrupt));
        when(contestRepo.findById(season.getMainContestId())).thenReturn(Optional.of(defaultContest));
        when(entryRepo.findByUserAndContest(userId, contestId))
                .thenReturn(Optional.of(com.ligitabl.model.domain.Entry.builder()
                        .id(UUID.randomUUID())
                        .userId(userId)
                        .contestId(contestId)
                        .joinedAtRound(0)
                        .build()));
        when(roundRepo.findById(season.getCurrentRoundId())).thenReturn(Optional.of(round));
        when(matchRepo.findByRoundId(round.getId())).thenReturn(List.of());

        Either<CreatePredictionError, CreatePredictionResult> result = useCase.execute(userId, multiSwap(List.of()));

        assertTrue(result.isLeft());
        assertInstanceOf(CreatePredictionError.CorruptPreSeasonRegistration.class, result.getLeft());
    }

    // --- Helpers ---

    private static CreatePredictionCommand singleSwap(String teamACode, String teamBCode) {
        return multiSwap(List.of(new CreatePredictionCommand.SwapPair(teamACode, teamBCode)));
    }

    private static CreatePredictionCommand multiSwap(List<CreatePredictionCommand.SwapPair> swaps) {
        return new CreatePredictionCommand(swaps);
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
