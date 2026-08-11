package com.ligitabl.api.rest.prediction.makeswap;

import static com.ligitabl.api.testsupport.TestCalendar.MID_SEASON;
import static com.ligitabl.api.testsupport.TestCalendar.SEASON_END;
import static com.ligitabl.api.testsupport.TestCalendar.SEASON_NAME;
import static com.ligitabl.api.testsupport.TestCalendar.SEASON_SLUG;
import static com.ligitabl.api.testsupport.TestCalendar.SEASON_START;
import static com.ligitabl.api.testsupport.TestIds.randomPublicId;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.offset;

import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
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
import com.ligitabl.api.shared.Either;
import com.ligitabl.api.testsupport.AbstractPostgresIT;
import com.ligitabl.api.testsupport.FixedClockConfig;
import com.ligitabl.api.testsupport.PostgresTestDbCleaner;
import com.ligitabl.model.domain.RoundStatus;
import com.ligitabl.model.domain.RoundSwap;
import com.ligitabl.model.domain.SeasonPrediction;
import com.ligitabl.model.domain.SwapChange;
import com.ligitabl.model.domain.TeamRank;
import com.ligitabl.model.repo.SeasonPredictionRepo;

@SpringBootTest
@Import(FixedClockConfig.class)
@DisplayName("MakeSwapUseCase Integration Tests")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class MakeSwapUseCaseIT extends AbstractPostgresIT {

    private static final List<TeamRank> RANKINGS = List.of(
            new TeamRank("MCI", 1),
            new TeamRank("ARS", 2),
            new TeamRank("LIV", 3),
            new TeamRank("AVL", 4),
            new TeamRank("CHE", 5),
            new TeamRank("NEW", 6),
            new TeamRank("MUN", 7),
            new TeamRank("TOT", 8),
            new TeamRank("BHA", 9),
            new TeamRank("CRY", 10),
            new TeamRank("BRE", 11),
            new TeamRank("WHU", 12));

    @Autowired
    MakeSwapUseCase useCase;

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

        insertUser(userId, "swap-user@example.com");
        insertCompetitionAndSeason();
        insertMainContest();
        insertRound(roundId, seasonId, 10, RoundStatus.OPEN);

        SeasonPrediction prediction = SeasonPrediction.builder()
                .userId(userId)
                .seasonId(seasonId)
                .initialRankings(RANKINGS)
                .currentRankings(RANKINGS)
                .swaps(List.of())
                .lastSwapAt(null)
                .atRoundNumber(10)
                // Joining stamps the round joined at, so a real row is never 0 here while
                // atRoundNumber is 10. Tests needing an unspent window override it explicitly.
                .openingCommittedRound(10)
                .build();

        predictionRepo.save(prediction);
    }

    private List<String> tableOrder(SeasonPrediction prediction) {
        return TeamRank.inPositionOrder(prediction.getCurrentRankings()).stream()
                .map(TeamRank::getCode)
                .toList();
    }

    @Nested
    @DisplayName("Success Cases")
    class SuccessCases {

        @Test
        @DisplayName("a swap moves exactly the two teams named and nothing else")
        void swapMovesOnlyTheTwoTeamsNamed() {
            useCase.execute(userId, new SwapCommand("ARS", "LIV"));

            assertThat(tableOrder(
                            predictionRepo.findByUserAndSeason(userId, seasonId).orElseThrow()))
                    .containsExactly(
                            "MCI", "LIV", "ARS", "AVL", "CHE", "NEW", "MUN", "TOT", "BHA", "CRY", "BRE", "WHU");
        }

        @Test
        @DisplayName("should allow first swap immediately")
        void shouldAllowFirstSwapImmediately() {
            SeasonPrediction beforeSwap =
                    predictionRepo.findByUserAndSeason(userId, seasonId).orElseThrow();
            assertThat(beforeSwap.getLastSwapAt()).isNull();
            assertThat(beforeSwap.getSwaps()).isEmpty();

            SwapCommand command = new SwapCommand("ARS", "LIV");

            Either<SwapError, SwapResult> result = useCase.execute(userId, command);

            assertThat(result.isRight()).isTrue();
            assertThat(result.get().success()).isTrue();
            assertThat(result.get().nextSwapAt()).isEqualTo(MID_SEASON.plus(Duration.ofHours(24)));
            assertThat(result.get().hoursUntilNext()).isEqualTo(24.0);
            assertThat(result.get().updatedPrediction()).isNotNull();

            SeasonPrediction persisted =
                    predictionRepo.findByUserAndSeason(userId, seasonId).orElseThrow();

            TeamRank ars = persisted.getCurrentRankings().stream()
                    .filter(t -> t.getCode().equals("ARS"))
                    .findFirst()
                    .orElseThrow();
            TeamRank liv = persisted.getCurrentRankings().stream()
                    .filter(t -> t.getCode().equals("LIV"))
                    .findFirst()
                    .orElseThrow();

            assertThat(ars.getPosition()).isEqualTo(3);
            assertThat(liv.getPosition()).isEqualTo(2);
            assertThat(persisted.getLastSwapAt()).isEqualTo(MID_SEASON);
            assertThat(persisted.getAtRoundNumber()).isEqualTo(10);

            assertThat(persisted.getSwaps()).hasSize(1);
            RoundSwap roundSwap = persisted.getSwaps().get(0);
            assertThat(roundSwap.getRound()).isEqualTo(10);
            assertThat(roundSwap.getChanges()).hasSize(1);
            SwapChange change = roundSwap.getChanges().get(0);
            assertThat(change.timestamp()).isEqualTo(MID_SEASON);
            assertThat(change.teamA()).isEqualTo("ARS:2→3");
            assertThat(change.teamB()).isEqualTo("LIV:3→2");
        }

        @Test
        @DisplayName("should update atRoundNumber to the target round when it was stale")
        void shouldUpdateAtRoundNumberToTargetRound() {
            SeasonPrediction prediction =
                    predictionRepo.findByUserAndSeason(userId, seasonId).orElseThrow();
            prediction.setAtRoundNumber(5); // stale from an earlier round, distinct from the target round (10)
            predictionRepo.save(prediction);

            Either<SwapError, SwapResult> result = useCase.execute(userId, new SwapCommand("ARS", "LIV"));

            assertThat(result.isRight()).isTrue();

            SeasonPrediction persisted =
                    predictionRepo.findByUserAndSeason(userId, seasonId).orElseThrow();
            assertThat(persisted.getAtRoundNumber()).isEqualTo(10);
        }

        @Test
        @DisplayName("should allow swap after cooldown expires")
        void shouldAllowSwapAfterCooldownExpires() {
            Either<SwapError, SwapResult> first = useCase.execute(userId, new SwapCommand("ARS", "LIV"));
            assertThat(first.isRight()).isTrue();

            SeasonPrediction afterFirst =
                    predictionRepo.findByUserAndSeason(userId, seasonId).orElseThrow();
            afterFirst.setLastSwapAt(MID_SEASON.minus(Duration.ofHours(24)));
            predictionRepo.save(afterFirst);

            Either<SwapError, SwapResult> second = useCase.execute(userId, new SwapCommand("MCI", "CHE"));
            assertThat(second.isRight()).isTrue();

            SeasonPrediction persisted =
                    predictionRepo.findByUserAndSeason(userId, seasonId).orElseThrow();
            assertThat(persisted.getSwaps()).hasSize(1);
            assertThat(persisted.getSwaps().get(0).getRound()).isEqualTo(10);
            assertThat(persisted.getSwaps().get(0).getChanges()).hasSize(2);
        }
    }

    @Nested
    @DisplayName("Validation Errors")
    class ValidationErrors {

        @Test
        @DisplayName("should reject swap when cooldown active")
        void shouldRejectWhenCooldownActive() {
            SeasonPrediction prediction =
                    predictionRepo.findByUserAndSeason(userId, seasonId).orElseThrow();
            prediction.setLastSwapAt(MID_SEASON.minus(Duration.ofHours(23)));
            prediction.setOpeningCommittedRound(10);
            predictionRepo.save(prediction);

            Either<SwapError, SwapResult> result = useCase.execute(userId, new SwapCommand("ARS", "LIV"));

            assertThat(result.isLeft()).isTrue();
            assertThat(result.getLeft()).isInstanceOf(SwapError.CooldownActive.class);

            SwapError.CooldownActive cooldownActive = (SwapError.CooldownActive) result.getLeft();
            assertThat(cooldownActive.nextSwapAt()).isEqualTo(MID_SEASON.plus(Duration.ofHours(1)));
            assertThat(cooldownActive.hoursRemaining()).isCloseTo(1.0, offset(0.01));
        }

        @Test
        @DisplayName("should reject an ordinary swap when the round's opening window is unspent")
        void shouldRejectWhenOpeningWindowNotYetUsed() {
            // The rule pairing this use case with RoundOpeningSwapUseCase: once the first-swap
            // bonus is gone, the round's opening window must be spent before ordinary swaps
            // resume.
            jdbcTemplate.update(
                    "UPDATE t_season_prediction SET c_last_swap_at = ?, c_opening_committed_round = 0"
                            + " WHERE fk_user_id = ?",
                    java.sql.Timestamp.from(MID_SEASON.minus(Duration.ofDays(2))),
                    userId);

            Either<SwapError, SwapResult> result = useCase.execute(userId, new SwapCommand("ARS", "LIV"));

            assertThat(result.isLeft()).isTrue();
            assertThat(result.getLeft()).isInstanceOf(SwapError.UseOpeningWindowFirst.class);
            assertThat(((SwapError.UseOpeningWindowFirst) result.getLeft()).round())
                    .isEqualTo(10);
        }

        @Test
        @DisplayName("should allow an ordinary swap once the opening window has been spent")
        void shouldAllowOnceOpeningWindowSpent() {
            // With the opening committed for this round — the cooldown
            // is the only remaining gate, and it has elapsed.
            jdbcTemplate.update(
                    "UPDATE t_season_prediction SET c_last_swap_at = ?, c_opening_committed_round = 10"
                            + " WHERE fk_user_id = ?",
                    java.sql.Timestamp.from(MID_SEASON.minus(Duration.ofDays(2))),
                    userId);

            assertThat(useCase.execute(userId, new SwapCommand("ARS", "LIV")).isRight())
                    .isTrue();
        }

        @Test
        @DisplayName("should reject swap when round not open")
        void shouldRejectWhenRoundNotOpen() {
            jdbcTemplate.update("UPDATE t_round SET c_is_finalized = true WHERE pk_id = ?", roundId);

            Either<SwapError, SwapResult> result = useCase.execute(userId, new SwapCommand("ARS", "LIV"));

            assertThat(result.isLeft()).isTrue();
            assertThat(result.getLeft()).isInstanceOf(SwapError.RoundNotOpen.class);
        }

        @Test
        @DisplayName("should reject swap when teams not found")
        void shouldRejectWhenTeamsNotFound() {
            // Use valid team codes, but remove one from the user's current rankings
            SeasonPrediction prediction =
                    predictionRepo.findByUserAndSeason(userId, seasonId).orElseThrow();
            List<TeamRank> withoutArs = new ArrayList<>(prediction.getCurrentRankings().stream()
                    .filter(t -> !t.getCode().equals("ARS"))
                    .toList());
            prediction.setCurrentRankings(withoutArs);
            predictionRepo.save(prediction);

            Either<SwapError, SwapResult> result = useCase.execute(userId, new SwapCommand("ARS", "LIV"));

            assertThat(result.isLeft()).isTrue();
            assertThat(result.getLeft()).isInstanceOf(SwapError.TeamsNotFound.class);
        }

        @Test
        @DisplayName("should reject swap when team code invalid")
        void shouldRejectWhenTeamCodeInvalid() {
            Either<SwapError, SwapResult> result = useCase.execute(userId, new SwapCommand("INVALID", "LIV"));

            assertThat(result.isLeft()).isTrue();
            assertThat(result.getLeft()).isInstanceOf(SwapError.InvalidTeamCode.class);

            SwapError.InvalidTeamCode error = (SwapError.InvalidTeamCode) result.getLeft();
            assertThat(error.code()).isEqualTo("INVALID");
        }

        @Test
        @DisplayName("should reject swap when no prediction exists")
        void shouldRejectWhenNoPredictionExists() {
            UUID otherUserId = UUID.randomUUID();
            insertUser(otherUserId, "no-prediction-user@example.com");

            Either<SwapError, SwapResult> result = useCase.execute(otherUserId, new SwapCommand("ARS", "LIV"));

            assertThat(result.isLeft()).isTrue();
            assertThat(result.getLeft()).isInstanceOf(SwapError.NoPredictionFound.class);

            SwapError.NoPredictionFound error = (SwapError.NoPredictionFound) result.getLeft();
            assertThat(error.userId()).isEqualTo(otherUserId);
            assertThat(error.seasonId()).isEqualTo(seasonId);
        }

        @Test
        @DisplayName("should reject swap when season completed")
        void shouldRejectWhenSeasonCompleted() {
            jdbcTemplate.update("UPDATE t_season SET c_completed = true WHERE pk_id = ?", seasonId);

            Either<SwapError, SwapResult> result = useCase.execute(userId, new SwapCommand("ARS", "LIV"));

            assertThat(result.isLeft()).isTrue();
            assertThat(result.getLeft()).isInstanceOf(SwapError.SeasonCompleted.class);
        }
    }

    private void insertCompetitionAndSeason() {
        String competitionSlug = competitionDefaults.defaultCompetitionSlug();
        jdbcTemplate.update(
                "INSERT INTO t_competition (pk_id, c_name, c_slug, c_code, c_phases, fk_active_season_id) VALUES (?,?,?,?, '[]'::jsonb, ?)",
                competitionId,
                "Premier League",
                competitionSlug,
                "PL",
                seasonId);

        jdbcTemplate.update(
                "INSERT INTO t_season (pk_id, c_client_id, fk_competition_id, c_name, c_slug, c_start_date, c_end_date, c_max_rounds, c_total_teams, c_initial_rankings, c_completed, fk_current_round_id, c_current_match_day) VALUES (?,?,?,?,?,?,?,?,?,?::jsonb,?,?,?)",
                seasonId,
                1,
                competitionId,
                SEASON_NAME,
                SEASON_SLUG,
                SEASON_START,
                SEASON_END,
                22,
                12,
                initialRankingsJson(),
                false,
                roundId,
                10);
    }

    private void insertMainContest() {
        UUID contestId = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO t_contest (pk_id, fk_season_id, c_name, c_is_private, c_join_code, c_from_round_position, c_to_round_position, c_max_entries) VALUES (?,?,?,?,?,?,?,?)",
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
        boolean isFinalized = status == RoundStatus.COMPLETED;
        jdbcTemplate.update(
                "INSERT INTO t_round (pk_id, fk_season_id, c_name, c_slug, c_position, c_is_finalized) VALUES (?,?,?,?,?,?)",
                id,
                seasonId,
                "Round " + position,
                "round-" + position,
                position,
                isFinalized);
    }

    private void insertUser(UUID id, String email) {
        jdbcTemplate.update(
                "INSERT INTO t_user (pk_id, c_email, c_password_hash, c_display_name, c_public_id, c_email_verified) VALUES (?,?,?,?,?,?)",
                id,
                email,
                "test-password-hash",
                "Swap User",
                randomPublicId(),
                true);

        jdbcTemplate.update("INSERT INTO t_user_role (fk_user_id, c_role) VALUES (?, ?)", id, "PLAYER");
    }

    private static String initialRankingsJson() {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < RANKINGS.size(); i++) {
            if (i > 0) {
                sb.append(",");
            }
            TeamRank tr = RANKINGS.get(i);
            sb.append("{\"code\":\"")
                    .append(tr.getCode())
                    .append("\",\"position\":")
                    .append(tr.getPosition())
                    .append("}");
        }
        sb.append("]");
        return sb.toString();
    }
}
