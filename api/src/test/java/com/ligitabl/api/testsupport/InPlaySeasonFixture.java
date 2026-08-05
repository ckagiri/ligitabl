package com.ligitabl.api.testsupport;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;

/**
 * The minimum database scaffold for an in-play season: competition, season, main contest and an
 * open round 1.
 *
 * <p>Extracted because the outbox ITs need identical scaffolding for opposite reasons — one
 * drives the happy path, one poisons it — and duplicating ~70 lines of INSERT between them made
 * it easy for the two to drift into testing subtly different worlds.
 *
 * <p>{@code preSeasonOpensAt} is set 30 days in the past on purpose: it is the anchor
 * {@code findUnjoinedUserIdsActiveSince} measures against, so a fixture without it would silently
 * select nobody.
 */
public final class InPlaySeasonFixture {

    private final JdbcTemplate jdbc;

    public final UUID competitionId = UUID.randomUUID();
    public final UUID seasonId = UUID.randomUUID();
    public final UUID contestId = UUID.randomUUID();
    public final UUID roundId = UUID.randomUUID();

    private InPlaySeasonFixture(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** Truncates, then writes a season sitting on an open round 1 with predictions already open. */
    public static InPlaySeasonFixture createFresh(JdbcTemplate jdbc, String competitionSlug) {
        PostgresTestDbCleaner.truncateAllDomainTables(jdbc);
        InPlaySeasonFixture fixture = new InPlaySeasonFixture(jdbc);
        fixture.insertCompetitionAndSeason(competitionSlug);
        fixture.insertMainContest();
        fixture.insertOpenRoundOne();
        return fixture;
    }

    /** A verified, mailable user last seen now — inside the auto-join eligibility window. */
    public UUID insertUser(String email) {
        UUID id = UUID.randomUUID();
        jdbc.update(
                "INSERT INTO t_user (pk_id, c_email, c_password_hash, c_display_name, c_public_id,"
                        + " c_email_verified, c_results_email_opt_out) VALUES (?,?,?,?,?,?,?)",
                id,
                email,
                "test-password-hash",
                "Test User",
                TestIds.randomPublicId(),
                true,
                false);
        jdbc.update("INSERT INTO t_user_role (fk_user_id, c_role) VALUES (?, ?)", id, "PLAYER");
        return id;
    }

    private void insertCompetitionAndSeason(String competitionSlug) {
        jdbc.update(
                "INSERT INTO t_competition (pk_id, c_name, c_slug, c_code, c_phases, fk_active_season_id)"
                        + " VALUES (?,?,?,?, '[]'::jsonb, ?)",
                competitionId,
                "Premier League",
                competitionSlug,
                "PL",
                seasonId);

        jdbc.update(
                "INSERT INTO t_season (pk_id, c_client_id, fk_competition_id, c_name, c_slug, c_start_date,"
                        + " c_end_date, c_max_rounds, c_total_teams, c_initial_rankings, c_completed,"
                        + " fk_current_round_id, c_current_match_day, c_pre_season_opens_at)"
                        + " VALUES (?,?,?,?,?,?,?,?,?,?::jsonb,?,?,?,?)",
                seasonId,
                1,
                competitionId,
                "2024/25",
                "2024-25",
                LocalDate.of(2024, 8, 1),
                LocalDate.of(2025, 5, 31),
                22,
                4,
                "[{\"code\":\"MCI\",\"position\":1},{\"code\":\"ARS\",\"position\":2},"
                        + "{\"code\":\"LIV\",\"position\":3},{\"code\":\"CHE\",\"position\":4}]",
                false,
                roundId,
                1,
                OffsetDateTime.now().minusDays(30));
    }

    private void insertMainContest() {
        jdbc.update(
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
        jdbc.update("UPDATE t_season SET fk_main_contest_id = ? WHERE pk_id = ?", contestId, seasonId);
    }

    private void insertOpenRoundOne() {
        jdbc.update(
                "INSERT INTO t_round (pk_id, fk_season_id, c_name, c_slug, c_position, c_is_finalized)"
                        + " VALUES (?,?,?,?,?,?)",
                roundId,
                seasonId,
                "Round 1",
                "round-1",
                1,
                false);
    }
}
