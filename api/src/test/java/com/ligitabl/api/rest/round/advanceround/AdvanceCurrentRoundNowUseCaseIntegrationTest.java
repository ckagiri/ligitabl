package com.ligitabl.api.rest.round.advanceround;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import com.ligitabl.api.shared.errors.ConflictError;
import com.ligitabl.api.shared.errors.NotFoundError;
import com.ligitabl.api.testsupport.AbstractPostgresIT;
import com.ligitabl.api.testsupport.PostgresTestDbCleaner;
import com.ligitabl.model.domain.Round;
import com.ligitabl.model.repo.RoundRepo;
import com.ligitabl.model.repo.SeasonRepo;

@SpringBootTest
@DisplayName("AdvanceCurrentRoundNowUseCase Integration Tests")
class AdvanceCurrentRoundNowUseCaseIntegrationTest extends AbstractPostgresIT {

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    AdvanceCurrentRoundNowUseCase useCase;

    @Autowired
    RoundRepo roundRepo;

    @Autowired
    SeasonRepo seasonRepo;

    @MockitoBean
    Clock clock;

    private UUID competitionId;
    private UUID seasonId;

    @BeforeEach
    void setup() {
        PostgresTestDbCleaner.truncateAllDomainTables(jdbc);
        when(clock.instant()).thenReturn(Instant.parse("2025-03-27T12:00:00Z"));

        competitionId = UUID.randomUUID();
        seasonId = UUID.randomUUID();
    }

    @Test
    @DisplayName("Should advance finalized current round to next round")
    void shouldAdvanceFinalizedRound() {
        Round round1 = setupSeasonWithRounds(2);
        Round round2 = createRound(2, false);
        setCurrentRound(round1.getId());

        var result = useCase.execute();

        assertThat(result.isRight()).isTrue();
        var response = result.get();
        assertThat(response.roundId()).isEqualTo(round1.getId());
        assertThat(response.fromPosition()).isEqualTo(1);
        assertThat(response.toPosition()).isEqualTo(2);
        assertThat(response.advancedAt()).isNotNull();

        var updatedRound = roundRepo.findById(round1.getId()).orElseThrow();
        assertThat(updatedRound.isAdvanced()).isTrue();
        assertThat(updatedRound.getAdvancedAt()).isNotNull();

        var season = seasonRepo.findById(seasonId).orElseThrow();
        assertThat(season.getCurrentRoundId()).isEqualTo(round2.getId());
    }

    @Test
    @DisplayName("Should return conflict when round is already advanced")
    void shouldReturnConflictWhenAlreadyAdvanced() {
        setupSeasonWithRounds(2);
        Round round1 = createRound(2, true);
        round1.setAdvanced(true);
        roundRepo.save(round1);
        setCurrentRound(round1.getId());

        var result = useCase.execute();

        assertThat(result.isLeft()).isTrue();
        assertThat(result.getLeft()).isInstanceOf(ConflictError.class);
    }

    @Test
    @DisplayName("Should return conflict when current round is not finalized")
    void shouldReturnConflictWhenNotFinalized() {
        Round round1 = setupSeasonWithRounds(2);
        // round1 not finalized by default in helper
        round1.setFinalized(false);
        roundRepo.save(round1);
        setCurrentRound(round1.getId());

        var result = useCase.execute();

        assertThat(result.isLeft()).isTrue();
        assertThat(result.getLeft()).isInstanceOf(ConflictError.class);
    }

    @Test
    @DisplayName("Should return not-found when no active competition exists")
    void shouldReturnNotFoundWhenNoCompetition() {
        // no DB setup — competition table is empty

        var result = useCase.execute();

        assertThat(result.isLeft()).isTrue();
        assertThat(result.getLeft()).isInstanceOf(NotFoundError.class);
    }

    @Test
    @DisplayName("Should mark last round advanced, not the season completed, when advancing the last round")
    void shouldMarkSeasonCompletedOnLastRound() {
        Round lastRound = setupSeasonWithRounds(1); // maxRounds = 1
        setCurrentRound(lastRound.getId());

        var result = useCase.execute();

        assertThat(result.isRight()).isTrue();

        // Season completion is a separate, explicit admin action — advancing the last round only
        // marks the round itself advanced.
        assertThat(roundRepo.findById(lastRound.getId()).orElseThrow().isAdvanced())
                .isTrue();
        var season = seasonRepo.findById(seasonId).orElseThrow();
        assertThat(season.isCompleted()).isFalse();
    }

    // ─── Helpers ────────────────────────────────────────────────────────────────

    /** Sets up competition + season (maxRounds=maxRounds) and creates round 1, finalized. */
    private Round setupSeasonWithRounds(int maxRounds) {
        insertCompetitionWithActiveSeason();
        insertSeason(maxRounds);
        return createRound(1, true);
    }

    private void insertSeason(int maxRounds) {
        jdbc.update(
                "INSERT INTO t_season (pk_id, c_client_id, fk_competition_id, c_name, c_slug,"
                        + " c_start_date, c_end_date, c_max_rounds, c_completed, c_completed_at,"
                        + " c_total_teams, c_max_hit_points, c_initial_rankings,"
                        + " fk_main_contest_id, fk_current_round_id, c_current_match_day)"
                        + " VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?::jsonb,?,?,?)",
                seasonId,
                ThreadLocalRandom.current().nextInt(1_000, 1_000_000),
                competitionId,
                "2024/25",
                "2024-25",
                LocalDate.of(2024, 8, 1),
                LocalDate.of(2025, 5, 31),
                maxRounds,
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

    private Round createRound(int position, boolean finalized) {
        return roundRepo.save(Round.builder()
                .seasonId(seasonId)
                .name("Matchday " + position)
                .slug("md-" + position)
                .position(position)
                .finalized(finalized)
                .build());
    }

    private void setCurrentRound(UUID roundId) {
        jdbc.update("UPDATE t_season SET fk_current_round_id = ? WHERE pk_id = ?", roundId, seasonId);
    }
}
