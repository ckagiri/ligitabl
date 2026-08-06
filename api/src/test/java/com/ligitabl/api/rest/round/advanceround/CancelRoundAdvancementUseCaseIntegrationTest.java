package com.ligitabl.api.rest.round.advanceround;

import static com.ligitabl.api.testsupport.FixedClockConfig.SEASON_END;
import static com.ligitabl.api.testsupport.FixedClockConfig.SEASON_NAME;
import static com.ligitabl.api.testsupport.FixedClockConfig.SEASON_SLUG;
import static com.ligitabl.api.testsupport.FixedClockConfig.SEASON_START;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

import com.ligitabl.api.shared.errors.ConflictError;
import com.ligitabl.api.shared.errors.NotFoundError;
import com.ligitabl.api.testsupport.AbstractPostgresIT;
import com.ligitabl.api.testsupport.FixedClockConfig;
import com.ligitabl.api.testsupport.PostgresTestDbCleaner;
import com.ligitabl.model.domain.Round;
import com.ligitabl.model.repo.RoundRepo;

@SpringBootTest
@Import(FixedClockConfig.class)
@DisplayName("CancelRoundAdvancementUseCase Integration Tests")
class CancelRoundAdvancementUseCaseIntegrationTest extends AbstractPostgresIT {


    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    CancelRoundAdvancementUseCase useCase;

    @Autowired
    RoundRepo roundRepo;

    @Autowired
    Clock clock;

    private UUID competitionId;
    private UUID seasonId;

    @BeforeEach
    void setup() {
        PostgresTestDbCleaner.truncateAllDomainTables(jdbc);

        competitionId = UUID.randomUUID();
        seasonId = UUID.randomUUID();
    }

    @Test
    @DisplayName("Should cancel scheduled advancement by clearing advanceAt")
    void shouldCancelScheduledAdvancement() {
        OffsetDateTime advanceAt = OffsetDateTime.now(ZoneOffset.UTC).plusMinutes(5);
        Round round = setupRound(true, false, advanceAt);
        setCurrentRound(round.getId());

        var result = useCase.execute();

        assertThat(result.isRight()).isTrue();
        assertThat(result.get().roundId()).isEqualTo(round.getId());

        var updated = roundRepo.findById(round.getId()).orElseThrow();
        assertThat(updated.getAdvanceAt()).isNull();
        assertThat(updated.isAdvanced()).isFalse();
    }

    @Test
    @DisplayName("Should return conflict when nothing is scheduled (advanceAt is null)")
    void shouldReturnConflictWhenNothingScheduled() {
        Round round = setupRound(true, false, null);
        setCurrentRound(round.getId());

        var result = useCase.execute();

        assertThat(result.isLeft()).isTrue();
        assertThat(result.getLeft()).isInstanceOf(ConflictError.class);
    }

    @Test
    @DisplayName("Should return conflict when round is already advanced")
    void shouldReturnConflictWhenAlreadyAdvanced() {
        Round round = setupRound(true, true, null);
        setCurrentRound(round.getId());

        var result = useCase.execute();

        assertThat(result.isLeft()).isTrue();
        assertThat(result.getLeft()).isInstanceOf(ConflictError.class);
    }

    @Test
    @DisplayName("Should return not-found when no active competition exists")
    void shouldReturnNotFoundWhenNoCompetition() {
        var result = useCase.execute();

        assertThat(result.isLeft()).isTrue();
        assertThat(result.getLeft()).isInstanceOf(NotFoundError.class);
    }

    // ─── Helpers ────────────────────────────────────────────────────────────────

    private Round setupRound(boolean finalized, boolean advanced, OffsetDateTime advanceAt) {
        insertCompetitionWithActiveSeason();
        insertSeason();
        return roundRepo.save(Round.builder()
                .seasonId(seasonId)
                .name("Matchday 1")
                .slug("md-1")
                .position(1)
                .finalized(finalized)
                .advanced(advanced)
                .advanceAt(advanceAt)
                .build());
    }

    private void insertSeason() {
        jdbc.update(
                "INSERT INTO t_season (pk_id, c_client_id, fk_competition_id, c_name, c_slug,"
                        + " c_start_date, c_end_date, c_max_rounds, c_completed, c_completed_at,"
                        + " c_total_teams, c_max_hit_points, c_initial_rankings,"
                        + " fk_main_contest_id, fk_current_round_id, c_current_match_day)"
                        + " VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?::jsonb,?,?,?)",
                seasonId,
                ThreadLocalRandom.current().nextInt(1_000, 1_000_000),
                competitionId,
                SEASON_NAME,
                SEASON_SLUG,
                SEASON_START,
                SEASON_END,
                3,
                false,
                null,
                4,
                220,
                "[]",
                null,
                null,
                1);
    }

    private void insertCompetitionWithActiveSeason() {
        jdbc.update(
                "INSERT INTO t_competition (pk_id, c_name, c_slug, c_code, c_phases, fk_active_season_id)"
                        + " VALUES (?,?,?,?,'[]'::jsonb,?)",
                competitionId,
                "Premier League",
                "premier-league",
                "PL",
                seasonId);
    }

    private void setCurrentRound(UUID roundId) {
        jdbc.update("UPDATE t_season SET fk_current_round_id = ? WHERE pk_id = ?", roundId, seasonId);
    }
}
