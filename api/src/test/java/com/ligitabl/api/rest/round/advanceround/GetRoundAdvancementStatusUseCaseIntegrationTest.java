package com.ligitabl.api.rest.round.advanceround;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import static org.mockito.Mockito.when;

import com.ligitabl.api.shared.errors.NotFoundError;
import com.ligitabl.api.testsupport.AbstractPostgresIT;
import com.ligitabl.api.testsupport.PostgresTestDbCleaner;
import com.ligitabl.model.domain.Round;
import com.ligitabl.model.domain.RoundStatus;
import com.ligitabl.model.repo.RoundRepo;

@SpringBootTest
@DisplayName("GetRoundAdvancementStatusUseCase Integration Tests")
class GetRoundAdvancementStatusUseCaseIntegrationTest extends AbstractPostgresIT {

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    GetRoundAdvancementStatusUseCase useCase;

    @Autowired
    RoundRepo roundRepo;

    @MockitoBean
    Clock clock;

    private static final Instant NOW = Instant.parse("2025-03-27T12:00:00Z");

    private UUID competitionId;
    private UUID seasonId;

    @BeforeEach
    void setup() {
        PostgresTestDbCleaner.truncateAllDomainTables(jdbc);
        when(clock.instant()).thenReturn(NOW);

        competitionId = UUID.randomUUID();
        seasonId = UUID.randomUUID();
    }

    @Test
    @DisplayName("Should return scheduled status with minutesRemaining when advanceAt is in the future")
    void shouldReturnScheduledWithMinutesRemaining() {
        OffsetDateTime advanceAt = OffsetDateTime.ofInstant(NOW, ZoneOffset.UTC).plusMinutes(10);
        Round round = setupRound(true, false, advanceAt);
        setCurrentRound(round.getId());

        var result = useCase.execute();

        assertThat(result.isRight()).isTrue();
        var status = result.get();
        assertThat(status.scheduled()).isTrue();
        assertThat(status.minutesRemaining()).isEqualTo(10);
        assertThat(status.advanced()).isFalse();
        assertThat(status.canOverride()).isTrue();
        assertThat(status.status()).isEqualTo(RoundStatus.FINALIZED);
    }

    @Test
    @DisplayName("Should return minutesRemaining=0 when advanceAt is in the past (overdue)")
    void shouldReturnOverdueWhenAdvanceAtInPast() {
        OffsetDateTime advanceAt = OffsetDateTime.ofInstant(NOW, ZoneOffset.UTC).minusMinutes(5);
        Round round = setupRound(true, false, advanceAt);
        setCurrentRound(round.getId());

        var result = useCase.execute();

        assertThat(result.isRight()).isTrue();
        var status = result.get();
        assertThat(status.scheduled()).isTrue();
        assertThat(status.minutesRemaining()).isEqualTo(0);
    }

    @Test
    @DisplayName("Should return not-scheduled when advanceAt is null")
    void shouldReturnNotScheduledWhenAdvanceAtNull() {
        Round round = setupRound(true, false, null);
        setCurrentRound(round.getId());

        var result = useCase.execute();

        assertThat(result.isRight()).isTrue();
        var status = result.get();
        assertThat(status.scheduled()).isFalse();
        assertThat(status.minutesRemaining()).isNull();
    }

    @Test
    @DisplayName("Should return advanced=true and canOverride=false when round is advanced")
    void shouldReturnAdvancedStatus() {
        Round round = setupRound(true, true, null);
        setCurrentRound(round.getId());

        var result = useCase.execute();

        assertThat(result.isRight()).isTrue();
        var status = result.get();
        assertThat(status.advanced()).isTrue();
        assertThat(status.canOverride()).isFalse();
        assertThat(status.scheduled()).isFalse();
        assertThat(status.status()).isEqualTo(RoundStatus.ADVANCED);
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
                "2024/25",
                "2024-25",
                LocalDate.of(2024, 8, 1),
                LocalDate.of(2025, 5, 31),
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
