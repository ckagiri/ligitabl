package com.ligitabl.api.rest.standings.calc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.JdbcTemplate;

import com.ligitabl.api.runners.calcstandings.CalcStandingsRunner;
import com.ligitabl.api.runners.calcstandings.CalculateRoundStandingsCommand;
import com.ligitabl.api.runners.calcstandings.CalculateRoundStandingsUseCase;
import com.ligitabl.api.shared.Either;
import com.ligitabl.api.testsupport.AbstractPostgresIT;
import com.ligitabl.api.testsupport.PostgresTestDbCleaner;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = {"workflow.run-calc-standings=true", "workflow.exit-after=false"})
class CalcStandingsRunnerIT extends AbstractPostgresIT {

    @Autowired
    CalcStandingsRunner runner;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @MockBean
    CalculateRoundStandingsUseCase calculateRoundStandingsUseCase;

    private UUID competitionId;
    private UUID seasonId;

    @BeforeEach
    void setupData() {
        PostgresTestDbCleaner.truncateAllDomainTables(jdbcTemplate);

        competitionId = UUID.randomUUID();
        seasonId = UUID.randomUUID();

        jdbcTemplate.update(
                "INSERT INTO t_competition (pk_id, c_name, c_slug, c_code, fk_active_season_id) VALUES (?,?,?,?,?)",
                competitionId,
                "Premier League",
                "premier-league",
                "PL",
                seasonId);

        jdbcTemplate.update(
                "INSERT INTO t_season (pk_id, c_client_id, fk_competition_id, c_name, c_slug, c_start_date, c_end_date, c_max_rounds, fk_current_round_id, c_current_match_day) "
                        + "VALUES (?,?,?,?,?,?,?,?,?,?)",
                seasonId,
                1,
                competitionId,
                "2024/25",
                "2024-25",
                LocalDate.of(2024, 8, 1),
                LocalDate.of(2025, 5, 31),
                38,
                null,
                1);

        jdbcTemplate.update(
                "INSERT INTO t_round (pk_id, fk_season_id, c_name, c_slug, c_position) VALUES (?,?,?,?,?)",
                UUID.randomUUID(),
                seasonId,
                "Matchday 1",
                "md-1",
                1);

        jdbcTemplate.update(
                "INSERT INTO t_round (pk_id, fk_season_id, c_name, c_slug, c_position) VALUES (?,?,?,?,?)",
                UUID.randomUUID(),
                seasonId,
                "Matchday 2",
                "md-2",
                2);
    }

    @Test
    void runnerBeanIsEnabled_andExecutesUseCasePerRound() throws Exception {
        assertThat(runner).isNotNull();

        // CalcStandingsRunner is an ApplicationRunner and is invoked once during Spring context startup.
        // Clear any invocations from that startup run so we can assert only the explicit run() below.
        reset(calculateRoundStandingsUseCase);

        when(calculateRoundStandingsUseCase.execute(any(CalculateRoundStandingsCommand.class)))
                .thenReturn(Either.right(List.of()));

        runner.run(new DefaultApplicationArguments(new String[] {}));

        verify(calculateRoundStandingsUseCase, times(2)).execute(any(CalculateRoundStandingsCommand.class));
    }
}
