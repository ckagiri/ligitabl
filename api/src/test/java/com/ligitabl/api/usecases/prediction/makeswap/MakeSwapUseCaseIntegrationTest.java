package com.ligitabl.api.usecases.prediction.makeswap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.offset;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
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
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.JdbcTemplate;

import com.ligitabl.api.config.CompetitionDefaults;
import com.ligitabl.api.shared.Either;
import com.ligitabl.api.testsupport.AbstractPostgresIT;
import com.ligitabl.api.testsupport.PostgresTestDbCleaner;
import com.ligitabl.model.domain.RoundStatus;
import com.ligitabl.model.domain.RoundSwap;
import com.ligitabl.model.domain.SeasonPrediction;
import com.ligitabl.model.domain.SwapChange;
import com.ligitabl.model.domain.TeamRank;
import com.ligitabl.model.repo.SeasonPredictionRepo;

@SpringBootTest
@DisplayName("MakeSwapUseCase Integration Tests")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class MakeSwapUseCaseIntegrationTest extends AbstractPostgresIT {

    private static final String SEASON_SLUG = "2024-25";

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

    @MockBean
    Clock clock;

    private UUID userId;
    private UUID competitionId;
    private UUID seasonId;
    private UUID roundId;

    private Instant now;

    @BeforeEach
    void setup() {
        PostgresTestDbCleaner.truncateAllDomainTables(jdbcTemplate);

        now = Instant.parse("2024-12-22T10:00:00Z");
        when(clock.instant()).thenReturn(now);

        userId = UUID.randomUUID();
        competitionId = UUID.randomUUID();
        seasonId = UUID.randomUUID();
        roundId = UUID.randomUUID();

        insertUser(userId, "swap-user@example.com");
        insertCompetitionAndSeason();
        insertRound(roundId, seasonId, 10, RoundStatus.OPEN);

        SeasonPrediction prediction = SeasonPrediction.builder()
                .userId(userId)
                .seasonId(seasonId)
                .initialRankings(RANKINGS)
                .currentRankings(RANKINGS)
                .swaps(List.of())
                .lastSwapAt(null)
                .atRoundNumber(0)
                .build();

        predictionRepo.save(prediction);
    }

    @Nested
    @DisplayName("Success Cases")
    class SuccessCases {

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
            assertThat(result.get().nextSwapAt()).isEqualTo(now.plus(Duration.ofHours(24)));
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
            assertThat(persisted.getLastSwapAt()).isEqualTo(now);
            assertThat(persisted.getAtRoundNumber()).isEqualTo(10);

            assertThat(persisted.getSwaps()).hasSize(1);
            RoundSwap roundSwap = persisted.getSwaps().get(0);
            assertThat(roundSwap.getRound()).isEqualTo(10);
            assertThat(roundSwap.getChanges()).hasSize(1);
            SwapChange change = roundSwap.getChanges().get(0);
            assertThat(change.timestamp()).isEqualTo(now);
            assertThat(change.teamA()).isEqualTo("ARS:2→3");
            assertThat(change.teamB()).isEqualTo("LIV:3→2");
        }

        @Test
        @DisplayName("should allow swap after cooldown expires")
        void shouldAllowSwapAfterCooldownExpires() {
            Either<SwapError, SwapResult> first = useCase.execute(userId, new SwapCommand("ARS", "LIV"));
            assertThat(first.isRight()).isTrue();

            SeasonPrediction afterFirst =
                    predictionRepo.findByUserAndSeason(userId, seasonId).orElseThrow();
            afterFirst.setLastSwapAt(now.minus(Duration.ofHours(24)));
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
            prediction.setLastSwapAt(now.minus(Duration.ofHours(23)));
            predictionRepo.save(prediction);

            Either<SwapError, SwapResult> result = useCase.execute(userId, new SwapCommand("ARS", "LIV"));

            assertThat(result.isLeft()).isTrue();
            assertThat(result.getLeft()).isInstanceOf(SwapError.CooldownActive.class);

            SwapError.CooldownActive cooldownActive = (SwapError.CooldownActive) result.getLeft();
            assertThat(cooldownActive.nextSwapAt()).isEqualTo(now.plus(Duration.ofHours(1)));
            assertThat(cooldownActive.hoursRemaining()).isCloseTo(1.0, offset(0.01));
        }

        @Test
        @DisplayName("should reject swap when round not open")
        void shouldRejectWhenRoundNotOpen() {
            jdbcTemplate.update("UPDATE t_round SET c_status = ? WHERE pk_id = ?", RoundStatus.LOCKED.name(), roundId);

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
                "2024/25",
                SEASON_SLUG,
                LocalDate.of(2024, 8, 1),
                LocalDate.of(2025, 5, 31),
                22,
                12,
                initialRankingsJson(),
                false,
                roundId,
                10);
    }

    private void insertRound(UUID id, UUID seasonId, int position, RoundStatus status) {
        jdbcTemplate.update(
                "INSERT INTO t_round (pk_id, fk_season_id, c_name, c_slug, c_position, c_status) VALUES (?,?,?,?,?,?)",
                id,
                seasonId,
                "Round " + position,
                "round-" + position,
                position,
                status.name());
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

    private static String randomPublicId() {
        String alphabet = "23456789ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnpqrstuvwxyz";
        StringBuilder sb = new StringBuilder(10);
        for (int i = 0; i < 10; i++) {
            int idx = java.util.concurrent.ThreadLocalRandom.current().nextInt(alphabet.length());
            sb.append(alphabet.charAt(idx));
        }
        return sb.toString();
    }
}
