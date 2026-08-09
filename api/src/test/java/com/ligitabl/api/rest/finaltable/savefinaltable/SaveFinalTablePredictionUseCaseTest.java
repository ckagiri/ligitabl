package com.ligitabl.api.rest.finaltable.savefinaltable;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.ligitabl.api.config.CompetitionDefaults;
import com.ligitabl.api.rest.finaltable.savefinaltable.SaveFinalTableCommand.SwapPair;
import com.ligitabl.api.rest.finaltable.shared.FinalTableError;
import com.ligitabl.api.rest.finaltable.shared.FinalTableSupport;
import com.ligitabl.api.rest.prediction.shared.SwapHelper;
import com.ligitabl.api.rest.round.shared.RoundSupport;
import com.ligitabl.api.rest.shared.HierarchyValidator;
import com.ligitabl.api.testsupport.TestCalendar;
import com.ligitabl.model.domain.FinalTablePrediction;
import com.ligitabl.model.domain.Match;
import com.ligitabl.model.domain.MatchStatus;
import com.ligitabl.model.domain.Round;
import com.ligitabl.model.domain.Season;
import com.ligitabl.model.domain.SwapChange;
import com.ligitabl.model.domain.TeamRank;
import com.ligitabl.model.repo.FinalTablePredictionRepo;
import com.ligitabl.model.repo.MatchRepo;
import com.ligitabl.model.repo.RoundRepo;
import com.ligitabl.model.repo.SeasonPredictionRepo;
import com.ligitabl.model.repo.SeasonRepo;

@ExtendWith(MockitoExtension.class)
class SaveFinalTablePredictionUseCaseTest {

    private final CompetitionDefaults competitionDefaults = new CompetitionDefaults("premier-league");

    @Mock
    private FinalTablePredictionRepo predictionRepo;

    @Mock
    private SeasonRepo seasonRepo;

    @Mock
    private RoundRepo roundRepo;

    @Mock
    private MatchRepo matchRepo;

    @Mock
    private HierarchyValidator hierarchyValidator;

    @Mock
    private SeasonPredictionRepo seasonPredictionRepo;

    private final Instant now = TestCalendar.MID_SEASON;
    private final Clock clock = Clock.fixed(now, ZoneOffset.UTC);

    private SaveFinalTablePredictionUseCase useCase;

    private UUID userId;
    private UUID seasonId;
    private UUID roundId;
    private Season season;
    private Round roundOne;

    /** The season baseline: ARS 1, LIV 2, MCI 3, CHE 4. */
    private static final List<TeamRank> BASELINE =
            List.of(TeamRank.of("ARS", 1), TeamRank.of("LIV", 2), TeamRank.of("MCI", 3), TeamRank.of("CHE", 4));

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        seasonId = UUID.randomUUID();
        roundId = UUID.randomUUID();

        season = Season.builder()
                .id(seasonId)
                .currentRoundId(roundId)
                .completed(false)
                .initialRankings(BASELINE)
                .mainContestId(UUID.randomUUID())
                .startDate(TestCalendar.SEASON_START)
                .endDate(TestCalendar.SEASON_END)
                .build();

        roundOne = Round.builder()
                .id(roundId)
                .seasonId(seasonId)
                .position(1)
                .finalized(false)
                .name("Round 1")
                .slug("round-1")
                .build();

        RoundSupport roundSupport = new RoundSupport(roundRepo, matchRepo, hierarchyValidator, competitionDefaults);

        useCase = new SaveFinalTablePredictionUseCase(
                predictionRepo,
                new FinalTableSupport(competitionDefaults, seasonRepo, roundRepo, roundSupport),
                new SwapHelper(competitionDefaults, seasonRepo, roundRepo, seasonPredictionRepo, roundSupport, clock),
                clock);
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private void stubOpenSeason() {
        when(seasonRepo.findActiveSeason("premier-league")).thenReturn(Optional.of(season));
        when(roundRepo.findBySeasonIdOrderByPosition(seasonId)).thenReturn(List.of(roundOne));
        when(matchRepo.findByRoundId(roundId)).thenReturn(List.of());
    }

    private void stubNoExistingRow() {
        when(predictionRepo.findByUserAndSeason(userId, seasonId)).thenReturn(Optional.empty());
    }

    private FinalTablePrediction existingRow(Instant settledAt) {
        return FinalTablePrediction.builder()
                .id(UUID.randomUUID())
                .userId(userId)
                .seasonId(seasonId)
                .rankings(new ArrayList<>(BASELINE))
                .settledAt(settledAt)
                .build();
    }

    private void stubExistingRow(FinalTablePrediction row) {
        when(predictionRepo.findByUserAndSeason(userId, seasonId)).thenReturn(Optional.of(row));
    }

    private void stubSaveEchoes() {
        when(predictionRepo.save(any())).thenAnswer(i -> i.getArgument(0));
    }

    private static SaveFinalTableCommand batch(List<SwapPair> swaps, String... expectedOrder) {
        return new SaveFinalTableCommand(swaps, List.of(expectedOrder));
    }

    // ── entry-open gating ─────────────────────────────────────────────────────

    @Nested
    class EntryGating {

        @Test
        void rejectsWhenRoundOneIsNoLongerOpen() {
            when(seasonRepo.findActiveSeason("premier-league")).thenReturn(Optional.of(season));
            when(roundRepo.findBySeasonIdOrderByPosition(seasonId)).thenReturn(List.of(roundOne));
            when(matchRepo.findByRoundId(roundId))
                    .thenReturn(List.of(match(MatchStatus.FINISHED), match(MatchStatus.FINISHED)));

            var result =
                    useCase.execute(userId, batch(List.of(new SwapPair("ARS", "LIV")), "LIV", "ARS", "MCI", "CHE"));

            assertTrue(result.isLeft());
            assertInstanceOf(FinalTableError.EntryClosed.class, result.getLeft());
            verify(predictionRepo, never()).save(any());
        }

        @Test
        void rejectsACompletedSeasonAsClosedRatherThanNotInPlay() {
            // A completed season is never IN_PLAY, so an isInPlay gate in activeSeason() would shadow
            // the lock predicate and report a merely-frozen table as "season not in play".
            season.setCompleted(true);
            when(seasonRepo.findActiveSeason("premier-league")).thenReturn(Optional.of(season));

            var result =
                    useCase.execute(userId, batch(List.of(new SwapPair("ARS", "LIV")), "LIV", "ARS", "MCI", "CHE"));

            assertTrue(result.isLeft());
            var error = assertInstanceOf(FinalTableError.EntryClosed.class, result.getLeft());
            assertEquals("COMPLETED", error.roundStatus());
        }

        @Test
        void rejectsAFinalizedRoundOneEvenWithNoMatchesLoaded() {
            // resolveStatus short-circuits to OPEN when a round has no match rows, before
            // computeStatus can report FINALIZED. Entry must follow join-eligibility semantics
            // instead, or a finalized round 1 would accept entries into an already-scored game.
            roundOne.setFinalized(true);
            when(seasonRepo.findActiveSeason("premier-league")).thenReturn(Optional.of(season));
            when(roundRepo.findBySeasonIdOrderByPosition(seasonId)).thenReturn(List.of(roundOne));

            var result =
                    useCase.execute(userId, batch(List.of(new SwapPair("ARS", "LIV")), "LIV", "ARS", "MCI", "CHE"));

            assertTrue(result.isLeft());
            var error = assertInstanceOf(FinalTableError.EntryClosed.class, result.getLeft());
            assertEquals("FINALIZED", error.roundStatus());
            verify(predictionRepo, never()).save(any());
            // The finalized flag decides it, so the match lookup is never needed.
            verify(matchRepo, never()).findByRoundId(any());
        }

        @Test
        void rejectsWhenNoActiveSeason() {
            when(seasonRepo.findActiveSeason("premier-league")).thenReturn(Optional.empty());

            var result = useCase.execute(userId, batch(List.of(new SwapPair("ARS", "LIV")), "LIV", "ARS"));

            assertTrue(result.isLeft());
            assertInstanceOf(FinalTableError.SeasonNotFound.class, result.getLeft());
        }

        @Test
        void refusesAUserWithNoRowOnceClosed() {
            // Not merely blocked from editing: the create path gates on the same predicate.
            when(seasonRepo.findActiveSeason("premier-league")).thenReturn(Optional.of(season));
            when(roundRepo.findBySeasonIdOrderByPosition(seasonId)).thenReturn(List.of(roundOne));
            when(matchRepo.findByRoundId(roundId)).thenReturn(List.of(match(MatchStatus.FINISHED)));

            var result = useCase.execute(userId, batch(List.of(), "ARS", "LIV", "MCI", "CHE"));

            assertTrue(result.isLeft());
            assertInstanceOf(FinalTableError.EntryClosed.class, result.getLeft());
            verify(predictionRepo, never()).findByUserAndSeason(any(), any());
        }

        @Test
        void rejectsAnAlreadyScoredRow() {
            stubOpenSeason();
            FinalTablePrediction scored = existingRow(now.minusSeconds(3600));
            scored.setScoredAt(now.minusSeconds(60));
            stubExistingRow(scored);

            var result =
                    useCase.execute(userId, batch(List.of(new SwapPair("ARS", "LIV")), "LIV", "ARS", "MCI", "CHE"));

            assertTrue(result.isLeft());
            assertInstanceOf(FinalTableError.AlreadyScored.class, result.getLeft());
            verify(predictionRepo, never()).save(any());
        }
    }

    // ── the empty-batch rule ──────────────────────────────────────────────────

    @Nested
    class EmptyBatch {

        @Test
        void firstSaveWithNoSwapsCreatesTheBaselineRow() {
            // The only empty batch the server accepts: accepting the baseline untouched.
            stubOpenSeason();
            stubNoExistingRow();
            stubSaveEchoes();

            var result = useCase.execute(userId, batch(List.of(), "ARS", "LIV", "MCI", "CHE"));

            assertTrue(result.isRight());
            FinalTablePrediction saved = result.get();
            assertEquals(0, saved.getSwapCount());
            assertTrue(saved.getSwaps().isEmpty());
            assertEquals(now, saved.getSettledAt());
            assertEquals(List.of("ARS", "LIV", "MCI", "CHE"), codes(saved));
        }

        @Test
        void rejectsAnEmptyBatchAgainstAnExistingNeverSwappedRow() {
            // swapCount == 0 must not be mistaken for "no row yet".
            stubOpenSeason();
            Instant createdAt = now.minusSeconds(86_400);
            FinalTablePrediction row = existingRow(createdAt);
            stubExistingRow(row);

            var result = useCase.execute(userId, batch(List.of(), "ARS", "LIV", "MCI", "CHE"));

            assertTrue(result.isLeft());
            assertInstanceOf(FinalTableError.NothingToSave.class, result.getLeft());
            verify(predictionRepo, never()).save(any());
            assertEquals(createdAt, row.getSettledAt());
        }

        @Test
        void anEmptyBatchNeverRewindsSettledAtOnASwappedRow() {
            // The regression that would silently corrupt the tiebreak: a player who swapped in
            // August and reopens the page must keep their August commitment time.
            stubOpenSeason();
            Instant august = now.minusSeconds(86_400 * 30);
            FinalTablePrediction row = existingRow(august.minusSeconds(3600));
            row.addSwap(new SwapChange(august, "ARS:1→2", "LIV:2→1"), august);
            stubExistingRow(row);

            var result = useCase.execute(userId, batch(List.of(), "LIV", "ARS", "MCI", "CHE"));

            assertTrue(result.isLeft());
            assertInstanceOf(FinalTableError.NothingToSave.class, result.getLeft());
            assertEquals(august, row.getSettledAt());
            assertEquals(1, row.getSwapCount());
            verify(predictionRepo, never()).save(any());
        }

        @Test
        void emptyBatchIsCheckedBeforeTheExpectedOrderChecksum() {
            // An empty batch has no codes to validate and must not fall through to a vacuous
            // comparison that passes whenever the client's order happens to match.
            stubOpenSeason();
            stubExistingRow(existingRow(now.minusSeconds(3600)));

            var result = useCase.execute(userId, batch(List.of(), "WRONG", "ORDER"));

            assertInstanceOf(FinalTableError.NothingToSave.class, result.getLeft());
        }
    }

    // ── replay, validation and the checksum ───────────────────────────────────

    @Nested
    class Replay {

        @Test
        void appliesABatchInOrderAndAdvancesSettledAt() {
            stubOpenSeason();
            Instant earlier = now.minusSeconds(86_400);
            FinalTablePrediction row = existingRow(earlier);
            stubExistingRow(row);
            stubSaveEchoes();

            var result = useCase.execute(
                    userId,
                    batch(List.of(new SwapPair("ARS", "LIV"), new SwapPair("MCI", "CHE")), "LIV", "ARS", "CHE", "MCI"));

            assertTrue(result.isRight());
            FinalTablePrediction saved = result.get();
            assertEquals(List.of("LIV", "ARS", "CHE", "MCI"), codes(saved));
            assertEquals(2, saved.getSwapCount());
            assertEquals(2, saved.getSwaps().size());
            assertEquals(now, saved.getSettledAt());
            assertTrue(saved.getSettledAt().isAfter(earlier));
        }

        @Test
        void recordsSwapChangesInTheExistingArrowFormat() {
            stubOpenSeason();
            stubExistingRow(existingRow(now.minusSeconds(3600)));
            stubSaveEchoes();

            var result =
                    useCase.execute(userId, batch(List.of(new SwapPair("ARS", "CHE")), "CHE", "LIV", "MCI", "ARS"));

            SwapChange change = result.get().getSwaps().get(0);
            assertEquals("ARS:1→4", change.teamA());
            assertEquals("CHE:4→1", change.teamB());
        }

        @Test
        void rejectsAnUnknownTeamCode() {
            stubOpenSeason();
            stubExistingRow(existingRow(now));

            var result =
                    useCase.execute(userId, batch(List.of(new SwapPair("ARS", "XYZ")), "ARS", "LIV", "MCI", "CHE"));

            assertTrue(result.isLeft());
            assertEquals("XYZ", ((FinalTableError.InvalidTeamCode) result.getLeft()).code());
            verify(predictionRepo, never()).save(any());
        }

        @Test
        void rejectsAPairNamingTheSameTeamTwice() {
            stubOpenSeason();
            stubExistingRow(existingRow(now));

            var result =
                    useCase.execute(userId, batch(List.of(new SwapPair("ARS", "ARS")), "ARS", "LIV", "MCI", "CHE"));

            assertTrue(result.isLeft());
            assertInstanceOf(FinalTableError.InvalidTeamCode.class, result.getLeft());
        }

        @Test
        void normalisesTeamCodeCasing() {
            stubOpenSeason();
            stubExistingRow(existingRow(now));
            stubSaveEchoes();

            var result =
                    useCase.execute(userId, batch(List.of(new SwapPair("ars", " liv ")), "LIV", "ARS", "MCI", "CHE"));

            assertTrue(result.isRight());
            assertEquals(List.of("LIV", "ARS", "MCI", "CHE"), codes(result.get()));
        }

        @Test
        void returnsOutOfSyncWhenTheChecksumDoesNotMatch() {
            // Another tab saved in between, so the replay lands somewhere the client did not expect.
            stubOpenSeason();
            stubExistingRow(existingRow(now.minusSeconds(3600)));

            var result =
                    useCase.execute(userId, batch(List.of(new SwapPair("ARS", "LIV")), "ARS", "LIV", "MCI", "CHE"));

            assertTrue(result.isLeft());
            var error = assertInstanceOf(FinalTableError.OutOfSync.class, result.getLeft());
            assertEquals(List.of("LIV", "ARS", "MCI", "CHE"), error.actualOrder());
            verify(predictionRepo, never()).save(any());
        }

        @Test
        void storesTheReplayedOrderRatherThanTheClientsExpectedOrder() {
            // expectedOrder is a checksum: writing it directly would make the swap list decorative.
            stubOpenSeason();
            stubExistingRow(existingRow(now));
            stubSaveEchoes();

            var result =
                    useCase.execute(userId, batch(List.of(new SwapPair("ARS", "LIV")), "LIV", "ARS", "MCI", "CHE"));

            assertTrue(result.isRight());
            List<TeamRank> stored = result.get().getRankings();
            assertEquals(
                    1,
                    stored.stream()
                            .filter(t -> t.getCode().equals("LIV"))
                            .findFirst()
                            .orElseThrow()
                            .getPosition());
        }

        @Test
        void aSwapAndItsInverseInOneBatchIsStillTwoRecordedSwaps() {
            // Net-zero on the table, but two real moves: swapCount and settledAt both reflect them.
            stubOpenSeason();
            stubExistingRow(existingRow(now.minusSeconds(3600)));
            stubSaveEchoes();

            var result = useCase.execute(
                    userId,
                    batch(List.of(new SwapPair("ARS", "LIV"), new SwapPair("ARS", "LIV")), "ARS", "LIV", "MCI", "CHE"));

            assertTrue(result.isRight());
            assertEquals(2, result.get().getSwapCount());
            assertEquals(now, result.get().getSettledAt());
        }
    }

    private static List<String> codes(FinalTablePrediction prediction) {
        return TeamRank.inPositionOrder(prediction.getRankings()).stream()
                .map(TeamRank::getCode)
                .toList();
    }

    private Match match(MatchStatus status) {
        return Match.builder()
                .id(UUID.randomUUID())
                .roundId(roundId)
                .seasonId(seasonId)
                .status(status)
                .build();
    }
}
