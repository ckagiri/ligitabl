package com.ligitabl.api.scheduling.outbox;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import com.ligitabl.api.testsupport.AbstractPostgresIT;
import com.ligitabl.api.testsupport.TestIds;
import com.ligitabl.api.testsupport.PostgresTestDbCleaner;
import com.ligitabl.model.domain.User;
import com.ligitabl.model.repo.UserRepo;

/**
 * The two auto-join queries are deliberately asymmetric — one ignores email flags, the
 * other enforces them — so they are tested side by side against the same fixture. A single
 * cohort of users is set up once and each query is asserted to select a different, exact
 * subset of it; that is the only way the asymmetry is visible as a property rather than as
 * two unrelated assertions.
 */
@SpringBootTest
@DisplayName("Auto-join user queries (real Postgres)")
class AutoJoinUserQueriesIT extends AbstractPostgresIT {

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    UserRepo userRepo;

    /** This season became joinable 30 days ago; every fixture user is placed either side of it. */
    private static final OffsetDateTime PRE_SEASON_OPENED = OffsetDateTime.now().minusDays(30);

    private UUID seasonId;
    private UUID otherSeasonId;

    private UUID activeNoPrediction;
    private UUID seenBeforePreSeasonOpened;
    private UUID activeOptedOutNoPrediction;
    private UUID activeUnverifiedNoPrediction;
    private UUID roundZeroVerified;
    private UUID roundZeroOptedOut;
    private UUID roundZeroUnverified;
    private UUID roundOneVerified;
    /**
     * Played last season, never joined this one, still logging in. The cohort this whole
     * feature exists for — and the one {@code findUnjoinedUserIdsAfter} misses, because it
     * filters on signup date and this account is several seasons old.
     */
    private UUID returningPlayerFromLastSeason;

    @BeforeEach
    void setup() {
        PostgresTestDbCleaner.truncateAllDomainTables(jdbc);

        UUID competitionId = UUID.randomUUID();
        seasonId = UUID.randomUUID();
        otherSeasonId = UUID.randomUUID();
        insertCompetition(competitionId);
        insertSeason(seasonId, competitionId, "2024-25", PRE_SEASON_OPENED);
        insertSeason(otherSeasonId, competitionId, "2023-24", PRE_SEASON_OPENED.minusYears(1));

        OffsetDateTime now = OffsetDateTime.now();

        activeNoPrediction = insertUser("active@x.com", now.minusDays(5), true, false);
        // Last in the app before this season existed — never saw it, never declined it.
        seenBeforePreSeasonOpened =
                insertUser("pre-window@x.com", now.minusDays(45), true, false, now.minusDays(45));
        activeOptedOutNoPrediction = insertUser("active-optout@x.com", now.minusDays(5), true, true);
        activeUnverifiedNoPrediction = insertUser("active-unverified@x.com", now.minusDays(5), false, false);

        roundZeroVerified = insertUser("r0-verified@x.com", now.minusDays(5), true, false);
        roundZeroOptedOut = insertUser("r0-optout@x.com", now.minusDays(5), true, true);
        roundZeroUnverified = insertUser("r0-unverified@x.com", now.minusDays(5), false, false);
        roundOneVerified = insertUser("r1-verified@x.com", now.minusDays(5), true, false);
        returningPlayerFromLastSeason = insertUser("returning-player@x.com", now.minusDays(5), true, false);

        insertPrediction(roundZeroVerified, seasonId, 0);
        insertPrediction(roundZeroOptedOut, seasonId, 0);
        insertPrediction(roundZeroUnverified, seasonId, 0);
        insertPrediction(roundOneVerified, seasonId, 1);
        insertPrediction(returningPlayerFromLastSeason, otherSeasonId, 0);
    }

    @Test
    @DisplayName("findUnjoinedUserIdsActiveSince: seen since pre-season opened, email flags ignored")
    void unjoinedActiveSinceIgnoresEmailFlags() {
        var ids = userRepo.findUnjoinedUserIdsActiveSince(seasonId, PRE_SEASON_OPENED);

        assertThat(ids)
                .as("opted-out and unverified users still deserve a table; last season's player "
                        + "counts because they have been back since this season opened")
                .containsExactlyInAnyOrder(
                        activeNoPrediction,
                        activeOptedOutNoPrediction,
                        activeUnverifiedNoPrediction,
                        returningPlayerFromLastSeason)
                .doesNotContain(seenBeforePreSeasonOpened, roundZeroVerified, roundOneVerified);

        // The mirror image, which is what shows the NOT EXISTS is genuinely season-scoped
        // rather than "has any prediction anywhere": ask about last season instead, and the
        // same users swap sides.
        assertThat(userRepo.findUnjoinedUserIdsActiveSince(otherSeasonId, PRE_SEASON_OPENED.minusYears(1)))
                .contains(roundZeroVerified, roundOneVerified)
                .doesNotContain(returningPlayerFromLastSeason);
    }

    @Test
    @DisplayName("findUnjoinedUserIdsActiveSince: last seen before pre-season opened is excluded")
    void lastSeenBeforePreSeasonOpenedIsExcluded() {
        // The distinction a rolling "last N days" window cannot make. This user was in the app
        // 45 days ago, which a 60-day window would have accepted — but 15 days before this
        // season became joinable, so they never saw it and never declined it. They get an
        // invitation (see the follow-up in .art/task_80.md), not a silent table.
        assertThat(userRepo.findUnjoinedUserIdsActiveSince(seasonId, PRE_SEASON_OPENED))
                .doesNotContain(seenBeforePreSeasonOpened);

        assertThat(userRepo.findUnjoinedUserIdsActiveSince(seasonId, PRE_SEASON_OPENED.minusDays(30)))
                .as("same user, earlier anchor — it is the anchor that excludes them, not the user")
                .contains(seenBeforePreSeasonOpened);
    }

    @Test
    @DisplayName("findUnjoinedUserIdsActiveSince: falls back to update_date when never logged in")
    void unjoinedActiveSinceUsesUpdateDateWhenNeverLoggedIn() {
        UUID touchedSincePreSeason =
                insertUser("never-logged-in-recent@x.com", null, true, false, PRE_SEASON_OPENED.plusDays(5));
        UUID untouchedSincePreSeason =
                insertUser("never-logged-in-old@x.com", null, true, false, PRE_SEASON_OPENED.minusDays(5));

        assertThat(userRepo.findUnjoinedUserIdsActiveSince(seasonId, PRE_SEASON_OPENED))
                .contains(touchedSincePreSeason)
                .doesNotContain(untouchedSincePreSeason);
    }

    @Test
    @DisplayName("findMailableUsersWithPreSeasonRegistration: round-0 and mailable only")
    void mailablePreSeasonRegistrationsAreRoundZeroAndMailable() {
        var users = userRepo.findMailableUsersWithPreSeasonRegistration(seasonId);

        assertThat(users.stream().map(User::getId))
                .containsExactly(roundZeroVerified)
                .as("opted-out and unverified are excluded; round 1 is already committed; "
                        + "another season's round-0 row is irrelevant")
                .doesNotContain(roundZeroOptedOut, roundZeroUnverified, roundOneVerified, returningPlayerFromLastSeason);
    }

    @Test
    @DisplayName("findMailableUsersWithPreSeasonRegistration: ignores last-seen entirely")
    void mailablePreSeasonRegistrationsIgnoreDormancy() {
        // A user who registered in pre-season and then vanished still gets welcomed — they
        // have a table in play and an unspent swap allowance.
        UUID longDormantRegistrant = insertUser(
                "r0-dormant@x.com", OffsetDateTime.now().minusDays(200), true, false, OffsetDateTime.now().minusDays(200));
        insertPrediction(longDormantRegistrant, seasonId, 0);

        assertThat(userRepo.findMailableUsersWithPreSeasonRegistration(seasonId).stream()
                        .map(User::getId))
                .containsExactlyInAnyOrder(roundZeroVerified, longDormantRegistrant);
    }

    private void insertCompetition(UUID id) {
        jdbc.update(
                "INSERT INTO t_competition (pk_id, c_name, c_slug, c_code, c_phases) VALUES (?,?,?,?, '[]'::jsonb)",
                id,
                "Premier League",
                "epl-queries-it",
                "PL");
    }

    private void insertSeason(UUID id, UUID competitionId, String slug, OffsetDateTime preSeasonOpensAt) {
        jdbc.update(
                "INSERT INTO t_season (pk_id, c_client_id, fk_competition_id, c_name, c_slug, c_start_date,"
                        + " c_end_date, c_max_rounds, c_total_teams, c_initial_rankings, c_completed,"
                        + " c_current_match_day, c_pre_season_opens_at) VALUES (?,?,?,?,?,?,?,?,?,?::jsonb,?,?,?)",
                id,
                1,
                competitionId,
                slug,
                slug,
                LocalDate.of(2024, 8, 1),
                LocalDate.of(2025, 5, 31),
                38,
                2,
                "[{\"code\":\"MCI\",\"position\":1},{\"code\":\"ARS\",\"position\":2}]",
                false,
                1,
                preSeasonOpensAt);
    }

    private UUID insertUser(String email, OffsetDateTime lastLoginAt, boolean verified, boolean optedOut) {
        return insertUser(email, lastLoginAt, verified, optedOut, OffsetDateTime.now());
    }

    /**
     * c_update_date must be set on INSERT: {@code trg_t_user_update_ts} is a BEFORE UPDATE
     * trigger that stamps now(), so any attempt to back-date it with an UPDATE is silently
     * overwritten.
     */
    private UUID insertUser(
            String email,
            OffsetDateTime lastLoginAt,
            boolean verified,
            boolean optedOut,
            OffsetDateTime updateDate) {
        UUID id = UUID.randomUUID();
        jdbc.update(
                "INSERT INTO t_user (pk_id, c_email, c_password_hash, c_display_name, c_public_id,"
                        + " c_email_verified, c_results_email_opt_out, c_last_login_at, c_update_date)"
                        + " VALUES (?,?,?,?,?,?,?,?,?)",
                id,
                email,
                "hash",
                "User",
                TestIds.randomPublicId(),
                verified,
                optedOut,
                lastLoginAt,
                updateDate);
        jdbc.update("INSERT INTO t_user_role (fk_user_id, c_role) VALUES (?, ?)", id, "PLAYER");
        return id;
    }

    private void insertPrediction(UUID userId, UUID seasonId, int atRoundNumber) {
        jdbc.update(
                "INSERT INTO t_season_prediction (pk_id, fk_user_id, fk_season_id, c_current_rankings, c_swaps,"
                        + " c_at_round_number) VALUES (?,?,?,?::jsonb,?::jsonb,?)",
                UUID.randomUUID(),
                userId,
                seasonId,
                "[{\"code\":\"MCI\",\"position\":1},{\"code\":\"ARS\",\"position\":2}]",
                "[]",
                atRoundNumber);
    }
}
