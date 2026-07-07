package com.ligitabl.api.rest.contest;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import com.ligitabl.api.testsupport.AbstractPostgresIT;
import com.ligitabl.api.testsupport.PostgresTestDbCleaner;
import com.ligitabl.model.domain.Contest;
import com.ligitabl.model.domain.Entry;
import com.ligitabl.model.domain.SeasonPrediction;
import com.ligitabl.model.repo.ContestRepo;
import com.ligitabl.model.repo.EntryRepo;
import com.ligitabl.model.repo.SeasonPredictionRepo;

@SpringBootTest
@DisplayName("ContestPersistenceAdapter Integration Tests")
class ContestPersistenceAdapterIT extends AbstractPostgresIT {

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    ContestRepo contestRepo;

    @Autowired
    EntryRepo entryRepo;

    @Autowired
    SeasonPredictionRepo predictionRepo;

    private UUID seasonId;
    private UUID userId;

    @BeforeEach
    void setup() {
        PostgresTestDbCleaner.truncateAllDomainTables(jdbc);

        seasonId = UUID.randomUUID();
        userId = UUID.randomUUID();

        insertCompetitionAndSeason();
        insertUser(userId, "user@test.com", "TestUser");
    }

    // ─── findByJoinCode ────────────────────────────────────────────────────────

    @Test
    @DisplayName("findByJoinCode is case-insensitive")
    void findByJoinCode_isCaseInsensitive() {
        Contest saved = contestRepo.save(privateContest("ab3K7pQ"));

        Optional<Contest> found = contestRepo.findByJoinCode("AB3K7PQ");

        assertThat(found).isPresent();
        assertThat(found.get().getId()).isEqualTo(saved.getId());
    }

    @Test
    @DisplayName("findByJoinCode returns empty for unknown code")
    void findByJoinCode_emptyForUnknownCode() {
        assertThat(contestRepo.findByJoinCode("XXXXXXX")).isEmpty();
    }

    @Test
    @DisplayName("findByJoinCode is case-insensitive both ways")
    void findByJoinCode_storedUpperLookedUpLower() {
        contestRepo.save(privateContest("ABCDEFG"));

        assertThat(contestRepo.findByJoinCode("abcdefg")).isPresent();
    }

    // ─── findPrivateByUserId ───────────────────────────────────────────────────

    @Test
    @DisplayName("findPrivateByUserId returns only private contests with active membership")
    void findPrivateByUserId_returnsActivePrivateContests() {
        insertPrediction(userId, 1);

        Contest privateContest = contestRepo.save(privateContest("CODE001"));
        entryRepo.save(Entry.builder()
                .userId(userId)
                .contestId(privateContest.getId())
                .joinedAtRound(1)
                .build());

        // Main (public) contest — should not appear
        UUID mainContestId = UUID.randomUUID();
        jdbc.update(
                "INSERT INTO t_contest (pk_id, fk_season_id, c_name, c_is_private, c_join_code, c_from_round_position, c_to_round_position, c_max_entries) VALUES (?,?,?,?,?,?,?,?)",
                mainContestId,
                seasonId,
                "Main League",
                false,
                null,
                1,
                38,
                null);
        entryRepo.save(Entry.builder()
                .userId(userId)
                .contestId(mainContestId)
                .joinedAtRound(1)
                .build());

        List<Contest> privateContests = contestRepo.findPrivateByUserId(userId);

        assertThat(privateContests).hasSize(1);
        assertThat(privateContests.get(0).getId()).isEqualTo(privateContest.getId());
    }

    @Test
    @DisplayName("findPrivateByUserId excludes contests the user was removed from")
    void findPrivateByUserId_excludesRemovedMemberships() {
        insertPrediction(userId, 1);

        Contest c1 = contestRepo.save(privateContest("Private1", "CODE001"));
        Contest c2 = contestRepo.save(privateContest("Private2", "CODE002"));

        entryRepo.save(Entry.builder()
                .userId(userId)
                .contestId(c1.getId())
                .joinedAtRound(1)
                .build());
        entryRepo.save(Entry.builder()
                .userId(userId)
                .contestId(c2.getId())
                .joinedAtRound(1)
                .build());

        // Soft-remove user from c2
        entryRepo.softRemove(userId, c2.getId(), 3);

        List<Contest> result = contestRepo.findPrivateByUserId(userId);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getId()).isEqualTo(c1.getId());
    }

    @Test
    @DisplayName("findPrivateByUserId returns empty when user has no active private memberships")
    void findPrivateByUserId_emptyWhenNoMemberships() {
        List<Contest> result = contestRepo.findPrivateByUserId(userId);
        assertThat(result).isEmpty();
    }

    // ─── findPrivateByUserId(userId, seasonId) ordering ───────────────────────

    @Test
    @DisplayName("findPrivateByUserId(userId, seasonId) orders in-progress, then upcoming, then completed")
    void findPrivateByUserId_withSeasonId_ordersByPhase() {
        setCurrentRoundPosition(5);

        Contest completed = contestRepo.save(privateContestWindow("Completed", "COMP001", 1, 3));
        Contest upcoming = contestRepo.save(privateContestWindow("Upcoming", "UPC0001", 8, 10));
        Contest inProgress = contestRepo.save(privateContestWindow("InProgress", "PROG001", 4, 6));

        joinContest(completed.getId());
        joinContest(upcoming.getId());
        joinContest(inProgress.getId());

        List<Contest> result = contestRepo.findPrivateByUserId(userId, seasonId);

        assertThat(result)
                .extracting(Contest::getId)
                .containsExactly(inProgress.getId(), upcoming.getId(), completed.getId());
    }

    @Test
    @DisplayName("findContestsByUserId orders active-tab private contests in-progress before upcoming")
    void findContestsByUserId_activeTab_ordersByPhase() {
        setCurrentRoundPosition(5);

        Contest upcoming = contestRepo.save(privateContestWindow("Upcoming", "UPC0002", 8, 10));
        Contest inProgress = contestRepo.save(privateContestWindow("InProgress", "PROG002", 4, 6));

        joinContest(upcoming.getId());
        joinContest(inProgress.getId());

        List<ContestRepo.UserContestView> result = contestRepo.findContestsByUserId(userId, seasonId, true, 10, 0);

        assertThat(result)
                .extracting(ContestRepo.UserContestView::contestId)
                .containsExactly(inProgress.getId(), upcoming.getId());
    }

    private void setCurrentRoundPosition(int position) {
        UUID roundId = UUID.randomUUID();
        jdbc.update(
                "INSERT INTO t_round (pk_id, fk_season_id, c_name, c_slug, c_position, c_is_finalized) VALUES (?,?,?,?,?,?)",
                roundId,
                seasonId,
                "Round " + position,
                "round-" + position,
                position,
                false);
        jdbc.update("UPDATE t_season SET fk_current_round_id = ? WHERE pk_id = ?", roundId, seasonId);
    }

    private Contest privateContestWindow(String name, String joinCode, int fromRound, int toRound) {
        return Contest.builder()
                .seasonId(seasonId)
                .name(name)
                .isPrivate(true)
                .isOpen(true)
                .joinCode(joinCode)
                .fromRoundPosition(fromRound)
                .toRoundPosition(toRound)
                .ownerId(userId)
                .build();
    }

    private void joinContest(UUID contestId) {
        entryRepo.save(Entry.builder()
                .userId(userId)
                .contestId(contestId)
                .joinedAtRound(1)
                .build());
    }

    // ─── save / delete ────────────────────────────────────────────────────────

    @Test
    @DisplayName("save persists ownerId and isOpen fields")
    void save_persistsOwnerIdAndIsOpen() {
        Contest contest = Contest.builder()
                .seasonId(seasonId)
                .name("Private")
                .isPrivate(true)
                .isOpen(true)
                .joinCode("NEWCODE")
                .fromRoundPosition(1)
                .toRoundPosition(10)
                .ownerId(userId)
                .build();

        Contest saved = contestRepo.save(contest);
        Optional<Contest> found = contestRepo.findById(saved.getId());

        assertThat(found).isPresent();
        assertThat(found.get().getOwnerId()).isEqualTo(userId);
        assertThat(found.get().isOpen()).isTrue();
    }

    @Test
    @DisplayName("delete removes the contest row")
    void delete_removesContestRow() {
        Contest contest = contestRepo.save(privateContest("DELCODE"));

        contestRepo.delete(contest.getId());

        assertThat(contestRepo.findById(contest.getId())).isEmpty();
    }

    // ─── Helpers ──────────────────────────────────────────────────────────────

    private Contest privateContest(String joinCode) {
        return privateContest("Private", joinCode);
    }

    private Contest privateContest(String name, String joinCode) {
        return Contest.builder()
                .seasonId(seasonId)
                .name(name)
                .isPrivate(true)
                .isOpen(true)
                .joinCode(joinCode)
                .fromRoundPosition(1)
                .toRoundPosition(10)
                .ownerId(userId)
                .build();
    }

    private void insertCompetitionAndSeason() {
        UUID competitionId = UUID.randomUUID();
        jdbc.update(
                "INSERT INTO t_competition (pk_id, c_name, c_slug, c_code, c_phases, fk_active_season_id) VALUES (?,?,?,?,'[]'::jsonb,?)",
                competitionId,
                "PL",
                "premier-league",
                "PL",
                seasonId);

        jdbc.update(
                "INSERT INTO t_season (pk_id, c_client_id, fk_competition_id, c_name, c_slug, c_start_date, c_end_date, c_max_rounds, c_total_teams, c_initial_rankings, c_completed, fk_current_round_id, c_current_match_day) VALUES (?,?,?,?,?,?,?,?,?,?::jsonb,?,?,?)",
                seasonId,
                1,
                competitionId,
                "2025/26",
                "2025-26",
                LocalDate.of(2025, 8, 1),
                LocalDate.of(2026, 5, 31),
                38,
                12,
                "[]",
                false,
                null,
                1);
    }

    private void insertUser(UUID id, String email, String displayName) {
        jdbc.update(
                "INSERT INTO t_user (pk_id, c_email, c_password_hash, c_display_name, c_public_id, c_email_verified) VALUES (?,?,?,?,?,?)",
                id,
                email,
                "hash",
                displayName,
                randomPublicId(),
                true);
        jdbc.update("INSERT INTO t_user_role (fk_user_id, c_role) VALUES (?,?)", id, "PLAYER");
    }

    private void insertPrediction(UUID userId, int atRound) {
        SeasonPrediction pred = SeasonPrediction.builder()
                .id(UUID.randomUUID())
                .userId(userId)
                .seasonId(seasonId)
                .initialRankings(List.of())
                .currentRankings(List.of())
                .swaps(List.of())
                .atRoundNumber(atRound)
                .build();
        predictionRepo.save(pred);
    }

    private static String randomPublicId() {
        String alpha = "23456789ABCDEFGHJKLMNPQRSTUVWXYZ";
        StringBuilder sb = new StringBuilder(10);
        for (int i = 0; i < 10; i++) {
            sb.append(alpha.charAt(
                    java.util.concurrent.ThreadLocalRandom.current().nextInt(alpha.length())));
        }
        return sb.toString();
    }
}
