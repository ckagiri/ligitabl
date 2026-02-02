package com.ligitabl.api.rest.match;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import com.ligitabl.api.testsupport.AbstractPostgresIT;
import com.ligitabl.api.testsupport.PostgresTestDbCleaner;
import com.ligitabl.model.domain.Match;
import com.ligitabl.model.domain.MatchStatus;
import com.ligitabl.model.domain.Score;
import com.ligitabl.model.repo.MatchRepo;

@SpringBootTest
class MatchRepositoryIntegrationTest extends AbstractPostgresIT {

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Autowired
    MatchRepo matchRepo;

    private UUID competitionId;
    private UUID seasonId;
    private UUID roundId;
    private UUID contestId;
    private UUID homeTeamId;
    private UUID awayTeamId;

    @BeforeEach
    void setupData() {
        PostgresTestDbCleaner.truncateAllDomainTables(jdbcTemplate);

        competitionId = UUID.randomUUID();
        seasonId = UUID.randomUUID();
        roundId = UUID.randomUUID();
        contestId = UUID.randomUUID();
        homeTeamId = UUID.randomUUID();
        awayTeamId = UUID.randomUUID();

        jdbcTemplate.update(
                "INSERT INTO t_team (pk_id, c_client_id, c_name, c_short_name, c_slug, c_tla) VALUES (?,?,?,?,?,?)",
                homeTeamId,
                1,
                "Home Team",
                "Home",
                "home-team",
                "HOM");

        jdbcTemplate.update(
                "INSERT INTO t_team (pk_id, c_client_id, c_name, c_short_name, c_slug, c_tla) VALUES (?,?,?,?,?,?)",
                awayTeamId,
                2,
                "Away Team",
                "Away",
                "away-team",
                "AWY");

        jdbcTemplate.update(
                "INSERT INTO t_competition (pk_id, c_name, c_slug, c_code, fk_active_season_id) VALUES (?,?,?,?,?)",
                competitionId,
                "Premier League",
                "premier-league",
                "PL",
                seasonId);

        jdbcTemplate.update(
                "INSERT INTO t_season (pk_id, c_client_id, fk_competition_id, c_name, c_slug, c_start_date, c_end_date, c_max_rounds, fk_current_round_id, c_current_match_day, fk_main_contest_id) "
                        + "VALUES (?,?,?,?,?,?,?,?,?,?,?)",
                seasonId,
                1,
                competitionId,
                "2024/25",
                "2024-25",
                LocalDate.of(2024, 8, 1),
                LocalDate.of(2025, 5, 31),
                38,
                roundId,
                1,
                null);

        jdbcTemplate.update(
                "INSERT INTO t_contest (pk_id, fk_season_id, c_name, c_is_private, c_join_code, c_from_round_position, c_to_round_position, c_max_entries) VALUES (?,?,?,?,?,?,?,?)",
                contestId,
                seasonId,
                "Main Contest",
                false,
                null,
                1,
                38,
                1_000);

        jdbcTemplate.update("UPDATE t_season SET fk_main_contest_id = ? WHERE pk_id = ?", contestId, seasonId);

        jdbcTemplate.update(
                "INSERT INTO t_round (pk_id, fk_season_id, c_name, c_slug, c_position, c_is_finalized) VALUES (?,?,?,?,?,?)",
                roundId,
                seasonId,
                "Matchday 1",
                "md-1",
                1,
                false);
    }

    @Test
    void save_persistsTransitionFlagsAndScore() {
        UUID matchId = UUID.randomUUID();
        String slug = "home-vs-away";

        jdbcTemplate.update(
                "INSERT INTO t_match (pk_id, c_client_id, fk_round_id, fk_home_team_id, fk_away_team_id, c_slug, c_status, c_kick_off, c_venue, c_matchday) "
                        + "VALUES (?,?,?,?,?,?,?,?,?,?)",
                matchId,
                1,
                roundId,
                homeTeamId,
                awayTeamId,
                slug,
                MatchStatus.SCHEDULED.name(),
                OffsetDateTime.now(),
                "Stadium",
                1);

        Match match = matchRepo.findByRoundIdAndSlug(roundId, slug).orElseThrow();
        match.transitionTo(MatchStatus.POSTPONED, "Rain");
        match.setScore(2, 1);
        match.transitionTo(MatchStatus.CANCELLED, "Cancelled");

        matchRepo.save(match);

        Match reloaded = matchRepo.findByRoundIdAndSlug(roundId, slug).orElseThrow();
        assertThat(reloaded.getStatus()).isEqualTo(MatchStatus.CANCELLED);
        assertThat(reloaded.isWasPostponed()).isTrue();
        assertThat(reloaded.getScore())
                .isEqualTo(Score.builder().homeGoals(2).awayGoals(1).build());
    }
}
