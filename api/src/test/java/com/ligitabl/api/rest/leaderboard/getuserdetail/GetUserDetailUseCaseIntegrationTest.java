package com.ligitabl.api.rest.leaderboard.getuserdetail;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import com.ligitabl.api.config.CompetitionDefaults;
import com.ligitabl.api.shared.Either;
import com.ligitabl.api.testsupport.AbstractPostgresIT;
import com.ligitabl.api.testsupport.PostgresTestDbCleaner;
import com.ligitabl.model.auth.PublicId;
import com.ligitabl.model.domain.Entry;
import com.ligitabl.model.domain.MatchStatus;
import com.ligitabl.model.domain.RoundResult;
import com.ligitabl.model.domain.RoundSubmission;
import com.ligitabl.model.domain.SeasonPrediction;
import com.ligitabl.model.domain.TeamRank;
import com.ligitabl.model.repo.EntryRepo;
import com.ligitabl.model.repo.RoundResultRepo;
import com.ligitabl.model.repo.RoundSubmissionRepo;
import com.ligitabl.model.repo.SeasonPredictionRepo;

@SpringBootTest
@DisplayName("GetUserDetailsUseCase Integration Tests")
class GetUserDetailUseCaseIntegrationTest extends AbstractPostgresIT {

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    GetUserDetailUseCase useCase;

    @Autowired
    CompetitionDefaults competitionDefaults;

    @Autowired
    EntryRepo entryRepo;

    @Autowired
    SeasonPredictionRepo seasonPredictionRepo;

    @Autowired
    RoundSubmissionRepo roundSubmissionRepo;

    @Autowired
    RoundResultRepo roundResultRepo;

    private UUID competitionId;
    private UUID seasonId;
    private UUID contestId;
    private UUID round1Id;
    private UUID round2Id;
    private UUID userId;
    private UUID homeTeamId;
    private UUID awayTeamId;

    private static final String USER_PUBLIC_ID = "AbCd3fGh9J";

    @BeforeEach
    void setup() {
        PostgresTestDbCleaner.truncateAllDomainTables(jdbc);

        competitionId = UUID.randomUUID();
        seasonId = UUID.randomUUID();
        contestId = UUID.randomUUID();
        round1Id = UUID.randomUUID();
        round2Id = UUID.randomUUID();
        userId = UUID.randomUUID();
        homeTeamId = UUID.randomUUID();
        awayTeamId = UUID.randomUUID();

        insertCompetitionSeasonAndContest();
        insertRound(round1Id, 1, true, true);
        insertRound(round2Id, 2, false, false);
        setCurrentRound(round2Id, 2);

        insertTeam(homeTeamId, "Arsenal", "arsenal", "ARS");
        insertTeam(awayTeamId, "Liverpool", "liverpool", "LIV");

        insertUser(userId, USER_PUBLIC_ID, "Alice");
        entryRepo.save(Entry.builder()
                .userId(userId)
                .contestId(contestId)
                .joinedAtRound(1)
                .build());

        UUID seasonPredictionId = UUID.randomUUID();
        seasonPredictionRepo.save(SeasonPrediction.builder()
                .id(seasonPredictionId)
                .userId(userId)
                .seasonId(seasonId)
                .initialRankings(List.of(new TeamRank("ARS", 1), new TeamRank("LIV", 2)))
                .currentRankings(List.of(new TeamRank("ARS", 1), new TeamRank("LIV", 2)))
                .swaps(List.of())
                .atRoundNumber(1)
                .build());

        RoundSubmission submission = roundSubmissionRepo.save(RoundSubmission.builder()
                .userId(userId)
                .seasonId(seasonId)
                .roundPosition(1)
                .rankings(List.of(new TeamRank("LIV", 1), new TeamRank("ARS", 2)))
                .seasonPredictionId(seasonPredictionId)
                .build());

        roundResultRepo.save(RoundResult.builder()
                .id(UUID.randomUUID())
                .roundSubmissionId(submission.getId())
                .rankings(List.of())
                .totalScore(10)
                .zeroesCount(0)
                .swapCount(0)
                .userViewed(false)
                .build());
    }

    @Test
    @DisplayName("uses season prediction when current round has no matches")
    void usesSeasonPredictionWhenCurrentRoundHasNoMatches() {
        Either<GetUserDetailError, GetUserDetailResult> result =
                useCase.execute(new GetUserDetailQuery(USER_PUBLIC_ID, null));

        assertThat(result.isRight()).isTrue();
        GetUserDetailResult details = result.get();
        assertThat(details.currentPrediction()).hasSize(2);
        assertThat(details.currentPrediction().get(0).teamName()).isEqualTo("Arsenal");
    }

    @Test
    @DisplayName("uses round submission when current round is locked")
    void usesRoundSubmissionWhenCurrentRoundLocked() {
        insertMatch(round2Id, MatchStatus.LIVE, 2);

        Either<GetUserDetailError, GetUserDetailResult> result =
                useCase.execute(new GetUserDetailQuery(USER_PUBLIC_ID, null));

        assertThat(result.isRight()).isTrue();
        GetUserDetailResult details = result.get();
        assertThat(details.currentPrediction()).hasSize(2);
        assertThat(details.currentPrediction().get(0).teamName()).isEqualTo("Liverpool");
    }

    private void insertCompetitionSeasonAndContest() {
        String phasesJson = "[{\"code\":\"FS\",\"name\":\"Full Season\",\"from\":1,\"to\":2}]";

        jdbc.update(
                "INSERT INTO t_competition (pk_id, c_name, c_slug, c_code, c_phases, fk_active_season_id) VALUES (?,?,?,?, ?::jsonb, ?)",
                competitionId,
                "Premier League",
                competitionDefaults.defaultCompetitionSlug(),
                "PL",
                phasesJson,
                seasonId);

        jdbc.update(
                "INSERT INTO t_season (pk_id, c_client_id, fk_competition_id, c_name, c_slug, c_start_date, c_end_date, c_max_rounds, c_total_teams, c_initial_rankings, c_completed, fk_current_round_id, c_current_match_day) VALUES (?,?,?,?,?,?,?,?,?,?::jsonb,?,?,?)",
                seasonId,
                1,
                competitionId,
                "2024/25",
                "2024-25",
                LocalDate.of(2024, 8, 1),
                LocalDate.of(2025, 5, 31),
                2,
                2,
                "[]",
                false,
                round2Id,
                2);

        jdbc.update(
                "INSERT INTO t_contest (pk_id, fk_season_id, c_name, c_is_private, c_join_code, c_from_round_position, c_to_round_position, c_max_entries) VALUES (?,?,?,?,?,?,?,?)",
                contestId,
                seasonId,
                "Main League",
                false,
                null,
                1,
                2,
                null);

        jdbc.update("UPDATE t_season SET fk_main_contest_id = ? WHERE pk_id = ?", contestId, seasonId);
    }

    private void insertRound(UUID roundId, int position, boolean finalized, boolean advanced) {
        jdbc.update(
                "INSERT INTO t_round (pk_id, fk_season_id, c_name, c_slug, c_position, c_is_finalized, c_advanced) VALUES (?,?,?,?,?,?,?)",
                roundId,
                seasonId,
                "Round " + position,
                "round-" + position,
                position,
                finalized,
                advanced);
    }

    private void setCurrentRound(UUID roundId, int matchDay) {
        jdbc.update(
                "UPDATE t_season SET fk_current_round_id = ?, c_current_match_day = ? WHERE pk_id = ?",
                roundId,
                matchDay,
                seasonId);
    }

    private void insertTeam(UUID teamId, String name, String slug, String tla) {
        jdbc.update(
                "INSERT INTO t_team (pk_id, c_name, c_short_name, c_slug, c_tla) VALUES (?,?,?,?,?)",
                teamId,
                name,
                name,
                slug,
                tla);
    }

    private void insertUser(UUID id, String publicId, String displayName) {
        jdbc.update(
                "INSERT INTO t_user (pk_id, c_email, c_password_hash, c_display_name, c_public_id, c_email_verified) VALUES (?,?,?,?,?,?)",
                id,
                publicId.toLowerCase() + "@example.com",
                "test-password-hash",
                displayName,
                PublicId.create(publicId).value(),
                true);
        jdbc.update("INSERT INTO t_user_role (fk_user_id, c_role) VALUES (?, ?)", id, "PLAYER");
    }

    private void insertMatch(UUID roundId, MatchStatus status, int matchDay) {
        jdbc.update(
                "INSERT INTO t_match (pk_id, c_client_id, fk_round_id, fk_home_team_id, fk_away_team_id, c_slug, c_status, c_kick_off, c_venue, c_matchday) VALUES (?,?,?,?,?,?,?,?,?,?)",
                UUID.randomUUID(),
                1,
                roundId,
                homeTeamId,
                awayTeamId,
                "arsenal-vs-liverpool-" + matchDay,
                status.name(),
                OffsetDateTime.now().withNano(0),
                "Test Stadium",
                matchDay);
    }
}
