package com.ligitabl.api.rest.prediction.roundopeningswap;

import static com.ligitabl.api.testsupport.TestCalendar.MID_SEASON;
import static com.ligitabl.api.testsupport.TestCalendar.SEASON_END;
import static com.ligitabl.api.testsupport.TestCalendar.SEASON_NAME;
import static com.ligitabl.api.testsupport.TestCalendar.SEASON_SLUG;
import static com.ligitabl.api.testsupport.TestCalendar.SEASON_START;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

import com.ligitabl.api.config.CompetitionDefaults;
import com.ligitabl.api.rest.prediction.makeswap.SwapCommand;
import com.ligitabl.api.rest.prediction.makeswap.SwapError;
import com.ligitabl.api.shared.Either;
import com.ligitabl.api.testsupport.AbstractPostgresIT;
import com.ligitabl.api.testsupport.FixedClockConfig;
import com.ligitabl.api.testsupport.PostgresTestDbCleaner;
import com.ligitabl.api.testsupport.TestIds;
import com.ligitabl.model.domain.RoundStatus;
import com.ligitabl.model.domain.SeasonPrediction;
import com.ligitabl.model.domain.TeamRank;
import com.ligitabl.model.repo.SeasonPredictionRepo;

/**
 * The round-opening swap is a separate allowance from the ordinary swap: 1–2 swaps, once per
 * round, and — unlike {@code MakeSwapUseCase} — with no cooldown gate. It is spent by setting
 * {@code openingCommittedRound}, which is also what
 * {@code CreatePredictionUseCase.mergePreSeasonRegistration} sets.
 */
@SpringBootTest
@Import(FixedClockConfig.class)
@DisplayName("RoundOpeningSwapUseCase Integration Tests")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class RoundOpeningSwapUseCaseIT extends AbstractPostgresIT {

    private static final int CURRENT_ROUND = 10;
    /** Eligible users entered in an earlier round — the window is for a table carried *into* one. */
    private static final int ENTERED_AT_ROUND = CURRENT_ROUND - 1;

    private static final List<TeamRank> RANKINGS = List.of(
            new TeamRank("MCI", 1),
            new TeamRank("ARS", 2),
            new TeamRank("LIV", 3),
            new TeamRank("AVL", 4),
            new TeamRank("CHE", 5),
            new TeamRank("NEW", 6));

    @Autowired
    RoundOpeningSwapUseCase useCase;

    @Autowired
    CompetitionDefaults competitionDefaults;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Autowired
    SeasonPredictionRepo predictionRepo;

    @Autowired
    Clock clock;

    private UUID userId;
    private UUID competitionId;
    private UUID seasonId;
    private UUID roundId;

    @BeforeEach
    void setup() {
        PostgresTestDbCleaner.truncateAllDomainTables(jdbcTemplate);

        userId = UUID.randomUUID();
        competitionId = UUID.randomUUID();
        seasonId = UUID.randomUUID();
        roundId = UUID.randomUUID();

        insertUser(userId, "opening-swap-user@example.com");
        insertCompetitionAndSeason();
        insertMainContest();
        insertRound(roundId, seasonId, CURRENT_ROUND, RoundStatus.OPEN);

        savePrediction(OPENING_UNUSED, null);
    }

    /**
     * {@code openingCommittedRound} is a primitive with no explicit "unused" value — 0 works
     * here only because round positions start at 1. Named here so the fixtures do not read as
     * arbitrary zeroes.
     */
    private static final int OPENING_UNUSED = 0;

    private static final int ROUND_ZERO = 0;

    /**
     * Replaces the prediction from {@link #setup()} — {@code uq_t_season_prediction_user_season}
     * means a second insert for the same user/season would violate the constraint.
     */
    private SeasonPrediction savePrediction(int openingCommittedRound, Instant lastSwapAt) {
        return savePrediction(openingCommittedRound, lastSwapAt, ENTERED_AT_ROUND);
    }

    private SeasonPrediction savePrediction(int openingCommittedRound, Instant lastSwapAt, int atRoundNumber) {
        jdbcTemplate.update("DELETE FROM t_season_prediction WHERE fk_user_id = ?", userId);
        SeasonPrediction prediction = SeasonPrediction.builder()
                .userId(userId)
                .seasonId(seasonId)
                .initialRankings(RANKINGS)
                .currentRankings(RANKINGS)
                .swaps(List.of())
                .lastSwapAt(lastSwapAt)
                .openingCommittedRound(openingCommittedRound)
                .atRoundNumber(atRoundNumber)
                .build();
        return predictionRepo.save(prediction);
    }

    private SeasonPrediction reload() {
        return predictionRepo.findByUserAndSeason(userId, seasonId).orElseThrow();
    }

    /** Codes in display order — see {@link TeamRank#inPositionOrder} for why list order is not it. */
    private List<String> tableOrder(SeasonPrediction prediction) {
        return TeamRank.inPositionOrder(prediction.getCurrentRankings()).stream()
                .map(TeamRank::getCode)
                .toList();
    }

    private Either<SwapError, RoundOpeningSwapResult> swap(String... codePairs) {
        List<SwapCommand> swaps = new java.util.ArrayList<>();
        for (int i = 0; i < codePairs.length; i += 2) {
            swaps.add(new SwapCommand(codePairs[i], codePairs[i + 1]));
        }
        return useCase.execute(userId, new RoundOpeningSwapCommand(swaps));
    }

    @Nested
    @DisplayName("Success Cases")
    class SuccessCases {

        @Test
        @DisplayName("a single swap applies and spends the opening window")
        void singleSwapAppliesAndSpendsTheWindow() {
            var result = swap("MCI", "ARS");

            assertThat(result.isRight()).isTrue();
            assertThat(result.get().swapsApplied()).isEqualTo(1);

            SeasonPrediction saved = reload();
            assertThat(tableOrder(saved)).containsExactly("ARS", "MCI", "LIV", "AVL", "CHE", "NEW");
            assertThat(saved.getOpeningCommittedRound())
                    .as("the window is spent by recording the round, not by a counter")
                    .isEqualTo(CURRENT_ROUND);
            assertThat(saved.getLastSwapAt()).isEqualTo(MID_SEASON);
            assertThat(saved.getAtRoundNumber())
                    .as("the window carries the table into this round")
                    .isEqualTo(CURRENT_ROUND);
        }

        @Test
        @DisplayName("two swaps apply in sequence, each recorded under the current round")
        void twoSwapsApplyInSequence() {
            var result = swap("MCI", "ARS", "LIV", "AVL");

            assertThat(result.isRight()).isTrue();
            assertThat(result.get().swapsApplied()).isEqualTo(2);

            SeasonPrediction saved = reload();
            assertThat(tableOrder(saved)).containsExactly("ARS", "MCI", "AVL", "LIV", "CHE", "NEW");
            assertThat(saved.getSwaps())
                    .as("both changes belong to the round the opening was used in")
                    .singleElement()
                    .satisfies(rs -> {
                        assertThat(rs.getRound()).isEqualTo(CURRENT_ROUND);
                        assertThat(rs.getChanges()).hasSize(2);
                    });
        }

        @Test
        @DisplayName("later swaps see the result of earlier ones in the same batch")
        void swapsCompoundWithinTheBatch() {
            // MCI<->ARS puts ARS first; then ARS<->LIV must move the *already swapped* ARS.
            var result = swap("MCI", "ARS", "ARS", "LIV");

            assertThat(result.isRight()).isTrue();
            assertThat(tableOrder(reload())).containsExactly("LIV", "MCI", "ARS", "AVL", "CHE", "NEW");
        }

        @Test
        @DisplayName("no cooldown gate: an opening swap is allowed right after an ordinary one")
        void openingWindowIgnoresTheSwapCooldown() {
            // The distinguishing behaviour vs MakeSwapUseCase, which rejects with CooldownActive.
            // The opening window is a separate allowance, not another ordinary swap.
            savePrediction(OPENING_UNUSED, MID_SEASON.minusSeconds(60));

            assertThat(swap("MCI", "ARS").isRight()).isTrue();
        }

        @Test
        @DisplayName("an opening used in an earlier round does not block this one")
        void openingFromAnEarlierRoundDoesNotCarryOver() {
            savePrediction(ENTERED_AT_ROUND, null);

            assertThat(swap("MCI", "ARS").isRight()).isTrue();
            assertThat(reload().getOpeningCommittedRound()).isEqualTo(CURRENT_ROUND);
        }
    }

    @Nested
    @DisplayName("Batch size")
    class BatchSize {

        @Test
        @DisplayName("rejects an empty batch")
        void rejectsEmptyBatch() {
            var result = useCase.execute(userId, new RoundOpeningSwapCommand(List.of()));

            assertThat(result.isLeft()).isTrue();
            assertThat(result.getLeft()).isInstanceOf(SwapError.BatchSizeInvalid.class);
            assertThat(reload().getOpeningCommittedRound())
                    .as("a rejected batch must not spend the window")
                    .isEqualTo(OPENING_UNUSED);
        }

        @Test
        @DisplayName("rejects a null swap list rather than treating it as empty")
        void rejectsNullBatch() {
            var result = useCase.execute(userId, new RoundOpeningSwapCommand(null));

            assertThat(result.isLeft()).isTrue();
            assertThat(result.getLeft()).isInstanceOf(SwapError.BatchSizeInvalid.class);
        }

        @Test
        @DisplayName("rejects three swaps")
        void rejectsMoreThanTwo() {
            var result = swap("MCI", "ARS", "LIV", "AVL", "CHE", "NEW");

            assertThat(result.isLeft()).isTrue();
            assertThat(((SwapError.BatchSizeInvalid) result.getLeft()).size()).isEqualTo(3);
            assertThat(tableOrder(reload()))
                    .as("nothing is applied when the batch is rejected")
                    .containsExactly("MCI", "ARS", "LIV", "AVL", "CHE", "NEW");
        }
    }

    @Nested
    @DisplayName("Rejections")
    class Rejections {

        @Test
        @DisplayName("rejects a second opening swap in the same round")
        void rejectsWhenOpeningAlreadyUsedThisRound() {
            assertThat(swap("MCI", "ARS").isRight()).isTrue();

            var second = swap("LIV", "AVL");

            assertThat(second.isLeft()).isTrue();
            assertThat(second.getLeft()).isInstanceOf(SwapError.OpeningAlreadyUsed.class);
            assertThat(tableOrder(reload()))
                    .as("the first swap stands; the second changes nothing")
                    .containsExactly("ARS", "MCI", "LIV", "AVL", "CHE", "NEW");
        }

        @Test
        @DisplayName("rejects when the round is no longer open")
        void rejectsWhenRoundNotOpen() {
            jdbcTemplate.update("UPDATE t_round SET c_is_finalized = true WHERE pk_id = ?", roundId);

            var result = swap("MCI", "ARS");

            assertThat(result.isLeft()).isTrue();
            assertThat(result.getLeft()).isInstanceOf(SwapError.RoundNotOpen.class);
        }

        @Test
        @DisplayName("rejects when the user has no prediction")
        void rejectsWhenNoPrediction() {
            jdbcTemplate.update("DELETE FROM t_season_prediction WHERE fk_user_id = ?", userId);

            var result = swap("MCI", "ARS");

            assertThat(result.isLeft()).isTrue();
            assertThat(result.getLeft()).isInstanceOf(SwapError.NoPredictionFound.class);
        }

        @Test
        @DisplayName("rejects an unknown team code")
        void rejectsUnknownTeamCode() {
            var result = swap("MCI", "ZZZ");

            assertThat(result.isLeft()).isTrue();
            assertThat(((SwapError.InvalidTeamCode) result.getLeft()).code()).isEqualTo("ZZZ");
        }

        @Test
        @DisplayName("rejects when the season is completed")
        void rejectsWhenSeasonCompleted() {
            jdbcTemplate.update("UPDATE t_season SET c_completed = true WHERE pk_id = ?", seasonId);

            var result = swap("MCI", "ARS");

            assertThat(result.isLeft()).isTrue();
            assertThat(result.getLeft()).isInstanceOf(SwapError.SeasonCompleted.class);
        }

        @Test
        @DisplayName("a rejected swap leaves the window unspent, so a valid one still works")
        void rejectionDoesNotSpendTheWindow() {
            assertThat(swap("MCI", "ZZZ").isLeft()).isTrue();

            assertThat(swap("MCI", "ARS").isRight())
                    .as("the failed attempt must not have consumed the round's opening")
                    .isTrue();
        }
    }

    @Nested
    @DisplayName("Interaction with pre-season registration")
    class PreSeasonRegistrationInteraction {

        @Test
        @DisplayName("merging at this round consumes its opening window")
        void mergeConsumesTheOpeningWindow() {
            // mergePreSeasonRegistration sets both atRoundNumber and openingCommittedRound to the
            // merge round.
            savePrediction(CURRENT_ROUND, MID_SEASON, CURRENT_ROUND);

            assertThat(swap("MCI", "ARS").getLeft()).isInstanceOf(SwapError.OpeningAlreadyUsed.class);
        }

        @Test
        @DisplayName("a legacy row with openingCommittedRound 0 is still rejected at its join round")
        void legacyRowWithoutCommittedRoundIsStillRejected() {
            // Rows predating the openingCommittedRound column (backfilled to 0) can have
            // atRoundNumber == this round while the field says 0 — which is why that field alone
            // cannot decide this: only the atRoundNumber check rejects here. Fresh joins stamp
            // openingCommittedRound = atRoundNumber and so trip either half of the OR.
            savePrediction(OPENING_UNUSED, MID_SEASON, CURRENT_ROUND);

            assertThat(swap("MCI", "ARS").getLeft()).isInstanceOf(SwapError.OpeningAlreadyUsed.class);
        }

        @Test
        @DisplayName("an unmerged pre-season registration must merge before using the window")
        void unmergedPreSeasonRegistrationMustMergeFirst() {
            // Round-0 row: the shape auto-registration writes. Allowing the swap would set
            // atRoundNumber and silently convert it to in-play, after which resolveJoinPlan
            // reports AlreadyJoined.
            savePrediction(OPENING_UNUSED, null, ROUND_ZERO);

            assertThat(swap("MCI", "ARS").getLeft()).isInstanceOf(SwapError.PreSeasonMergeRequired.class);
            assertThat(reload().getAtRoundNumber())
                    .as("the row must stay a pre-season registration")
                    .isZero();
        }
    }

    private void insertCompetitionAndSeason() {
        jdbcTemplate.update(
                "INSERT INTO t_competition (pk_id, c_name, c_slug, c_code, c_phases, fk_active_season_id)"
                        + " VALUES (?,?,?,?, '[]'::jsonb, ?)",
                competitionId,
                "Premier League",
                competitionDefaults.defaultCompetitionSlug(),
                "PL",
                seasonId);

        jdbcTemplate.update(
                "INSERT INTO t_season (pk_id, c_client_id, fk_competition_id, c_name, c_slug, c_start_date,"
                        + " c_end_date, c_max_rounds, c_total_teams, c_initial_rankings, c_completed,"
                        + " fk_current_round_id, c_current_match_day) VALUES (?,?,?,?,?,?,?,?,?,?::jsonb,?,?,?)",
                seasonId,
                1,
                competitionId,
                SEASON_NAME,
                SEASON_SLUG,
                SEASON_START,
                SEASON_END,
                22,
                RANKINGS.size(),
                initialRankingsJson(),
                false,
                roundId,
                CURRENT_ROUND);
    }

    private void insertMainContest() {
        UUID contestId = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO t_contest (pk_id, fk_season_id, c_name, c_is_private, c_join_code,"
                        + " c_from_round_position, c_to_round_position, c_max_entries) VALUES (?,?,?,?,?,?,?,?)",
                contestId,
                seasonId,
                "Main League",
                false,
                null,
                1,
                22,
                null);
        jdbcTemplate.update("UPDATE t_season SET fk_main_contest_id = ? WHERE pk_id = ?", contestId, seasonId);
    }

    private void insertRound(UUID id, UUID seasonId, int position, RoundStatus status) {
        jdbcTemplate.update(
                "INSERT INTO t_round (pk_id, fk_season_id, c_name, c_slug, c_position, c_is_finalized)"
                        + " VALUES (?,?,?,?,?,?)",
                id,
                seasonId,
                "Round " + position,
                "round-" + position,
                position,
                status == RoundStatus.COMPLETED);
    }

    private void insertUser(UUID id, String email) {
        jdbcTemplate.update(
                "INSERT INTO t_user (pk_id, c_email, c_password_hash, c_display_name, c_public_id, c_email_verified)"
                        + " VALUES (?,?,?,?,?,?)",
                id,
                email,
                "test-password-hash",
                "Opening Swap User",
                TestIds.randomPublicId(),
                true);
        jdbcTemplate.update("INSERT INTO t_user_role (fk_user_id, c_role) VALUES (?, ?)", id, "PLAYER");
    }

    private static String initialRankingsJson() {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < RANKINGS.size(); i++) {
            if (i > 0) {
                sb.append(",");
            }
            sb.append("{\"code\":\"")
                    .append(RANKINGS.get(i).getCode())
                    .append("\",\"position\":")
                    .append(RANKINGS.get(i).getPosition())
                    .append("}");
        }
        return sb.append("]").toString();
    }
}
