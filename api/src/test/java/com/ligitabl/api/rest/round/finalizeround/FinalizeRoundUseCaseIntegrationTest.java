package com.ligitabl.api.rest.round.finalizeround;

import static com.ligitabl.api.testsupport.TestIds.randomPublicId;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ligitabl.api.scheduling.advanceround.RoundAdvancementService;
import com.ligitabl.api.testsupport.AbstractPostgresIT;
import com.ligitabl.api.testsupport.PostgresTestDbCleaner;
import com.ligitabl.model.domain.Match;
import com.ligitabl.model.domain.MatchStatus;
import com.ligitabl.model.domain.Round;
import com.ligitabl.model.domain.Score;
import com.ligitabl.model.domain.SeasonPrediction;
import com.ligitabl.model.domain.Standings;
import com.ligitabl.model.domain.Team;
import com.ligitabl.model.domain.TeamRank;
import com.ligitabl.model.domain.TeamSlug;
import com.ligitabl.model.repo.MatchRepo;
import com.ligitabl.model.repo.RoundRepo;
import com.ligitabl.model.repo.RoundResultRepo;
import com.ligitabl.model.repo.RoundSubmissionRepo;
import com.ligitabl.model.repo.SeasonPredictionRepo;
import com.ligitabl.model.repo.SeasonRepo;
import com.ligitabl.model.repo.StandingsRepo;
import com.ligitabl.model.repo.TeamRepo;

@SpringBootTest
@DisplayName("FinalizeRoundUseCase Integration Tests")
class FinalizeRoundUseCaseIntegrationTest extends AbstractPostgresIT {

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    FinalizeRoundUseCase finalizeRoundUseCase;

    @Autowired
    RoundAdvancementService roundAdvancementService;

    @Autowired
    SeasonRepo seasonRepo;

    @Autowired
    TeamRepo teamRepo;

    @Autowired
    RoundRepo roundRepo;

    @Autowired
    MatchRepo matchRepo;

    @Autowired
    StandingsRepo standingsRepo;

    @Autowired
    SeasonPredictionRepo seasonPredictionRepo;

    @Autowired
    RoundSubmissionRepo roundSubmissionRepo;

    @Autowired
    RoundResultRepo roundResultRepo;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private UUID competitionId;
    private UUID seasonId;

    private Team arsenal;
    private Team chelsea;
    private Team liverpool;
    private Team manCity;

    private UUID aliceId;
    private UUID bobId;

    @BeforeEach
    void setup() throws Exception {
        PostgresTestDbCleaner.truncateAllDomainTables(jdbc);

        competitionId = UUID.randomUUID();
        seasonId = UUID.randomUUID();

        insertCompetition();

        arsenal = createTeam("ARS", "Arsenal");
        chelsea = createTeam("CHE", "Chelsea");
        liverpool = createTeam("LIV", "Liverpool");
        manCity = createTeam("MCI", "Manchester City");

        aliceId = insertUser("alice@example.com", "Alice");
        bobId = insertUser("bob@example.com", "Bob");
    }

    @Test
    @DisplayName("Should successfully finalize a locked round with finished matches")
    void shouldSuccessfullyFinalizeRound() throws Exception {
        insertSeason(2, 4);

        Round round1 = createRound(1, false);
        createRound(2, false);
        setCurrentRound(round1.getId());

        createFinishedMatch(round1, arsenal, chelsea, 2, 1);
        createFinishedMatch(round1, liverpool, manCity, 1, 1);

        createPrediction(aliceId, 1);
        createPrediction(bobId, 1);

        var result = finalizeRoundUseCase.execute(seasonId);

        assertThat(result.isRight()).isTrue();
        var response = result.get();

        assertThat(response.roundId()).isEqualTo(round1.getId());
        assertThat(response.roundPosition()).isEqualTo(1);
        assertThat(response.submissionsCreated()).isEqualTo(2);
        assertThat(response.resultsCalculated()).isEqualTo(2);
        assertThat(response.seasonCompleted()).isFalse();

        var updatedRound1 = roundRepo.findById(round1.getId()).orElseThrow();
        assertThat(updatedRound1.isFinalized()).isTrue();

        var standings = standingsRepo.findBySeasonAndRoundPosition(seasonId, 1);
        assertThat(standings).isPresent();
        assertThat(standings.get().isFinalised()).isTrue();
        assertThat(standings.get().getRankings()).hasSize(4);

        var submissions = roundSubmissionRepo.findBySeasonAndRound(seasonId, 1);
        assertThat(submissions).hasSize(2);
        for (var submission : submissions) {
            assertThat(roundResultRepo.findByRoundSubmissionId(submission.getId()))
                    .isPresent();
        }

        // finalization no longer advances season pointers
        var season = seasonRepo.findById(seasonId).orElseThrow();
        assertThat(season.getCurrentRoundId()).isEqualTo(round1.getId());
    }

    @Test
    @DisplayName("Should return error when round is not locked")
    void shouldReturnErrorWhenRoundNotLocked() throws Exception {
        insertSeason(2, 4);

        Round round1 = createRound(1, false);
        createRound(2, false);
        setCurrentRound(round1.getId());

        createScheduledMatch(round1, arsenal, chelsea);
        createPrediction(aliceId, 1);

        var result = finalizeRoundUseCase.execute(seasonId);

        assertThat(result.isLeft()).isTrue();
        assertThat(result.getLeft()).isInstanceOf(FinalizeRoundError.RoundNotReady.class);
    }

    @Test
    @DisplayName("Should return error when round is already finalized")
    void shouldReturnErrorWhenAlreadyFinalized() throws Exception {
        insertSeason(2, 4);

        Round round1 = createRound(1, true);
        createRound(2, false);
        setCurrentRound(round1.getId());

        var result = finalizeRoundUseCase.execute(seasonId);

        assertThat(result.isLeft()).isTrue();
        assertThat(result.getLeft()).isInstanceOf(FinalizeRoundError.AlreadyFinalized.class);
    }

    @Test
    @DisplayName("Should return error when cancelled matches exist")
    void shouldReturnErrorWhenCancelledMatchesExist() throws Exception {
        insertSeason(2, 4);

        Round round1 = createRound(1, false);
        createRound(2, false);
        setCurrentRound(round1.getId());

        createFinishedMatch(round1, arsenal, chelsea, 2, 1);
        createCancelledMatch(round1, liverpool, manCity);

        var result = finalizeRoundUseCase.execute(seasonId);

        assertThat(result.isLeft()).isTrue();
        assertThat(result.getLeft()).isInstanceOf(FinalizeRoundError.RoundObstructed.class);
    }

    @Test
    @DisplayName("Should return error when round not found")
    void shouldReturnErrorWhenRoundNotFound() throws Exception {
        insertSeason(2, 4);

        UUID missingRoundId = UUID.randomUUID();
        setCurrentRound(missingRoundId);

        var result = finalizeRoundUseCase.execute(seasonId);

        assertThat(result.isLeft()).isTrue();
        assertThat(result.getLeft()).isInstanceOf(FinalizeRoundError.RoundNotFound.class);

        var error = (FinalizeRoundError.RoundNotFound) result.getLeft();
        assertThat(error.roundId()).isEqualTo(missingRoundId);
    }

    @Test
    @DisplayName("Should calculate and persist correct standings")
    void shouldCalculateAndPersistCorrectStandings() throws Exception {
        insertSeason(2, 4);

        Round round1 = createRound(1, false);
        createRound(2, false);
        setCurrentRound(round1.getId());

        // Arsenal 3-1 Chelsea (Arsenal wins)
        createFinishedMatch(round1, arsenal, chelsea, 3, 1);
        // Liverpool 2-2 Man City (draw)
        createFinishedMatch(round1, liverpool, manCity, 2, 2);

        var result = finalizeRoundUseCase.execute(seasonId);

        assertThat(result.isRight()).isTrue();

        Standings standings =
                standingsRepo.findBySeasonAndRoundPosition(seasonId, 1).orElseThrow();
        assertThat(standings.isFinalised()).isTrue();
        assertThat(standings.getRankings()).hasSize(4);

        // Arsenal: 3 points, GD +2
        var arsenalRank = standings.findByTeamCode("ARS").orElseThrow();
        assertThat(arsenalRank.getMetadata().getPoints()).isEqualTo(3);
        assertThat(arsenalRank.getMetadata().getGd()).isEqualTo(2);

        // Chelsea: 0 points
        var chelseaRank = standings.findByTeamCode("CHE").orElseThrow();
        assertThat(chelseaRank.getMetadata().getPoints()).isEqualTo(0);

        // Top and bottom sanity
        assertThat(standings.getRankings().get(0).teamCode()).isEqualTo("ARS");
        assertThat(standings
                        .getRankings()
                        .get(standings.getRankings().size() - 1)
                        .teamCode())
                .isEqualTo("CHE");
    }

    @Test
    @DisplayName("Should calculate user prediction results correctly")
    void shouldCalculateUserPredictionResults() throws Exception {
        insertSeason(2, 4);

        Round round1 = createRound(1, false);
        createRound(2, false);
        setCurrentRound(round1.getId());

        createFinishedMatch(round1, arsenal, chelsea, 2, 1);
        createFinishedMatch(round1, liverpool, manCity, 1, 1);

        createPrediction(aliceId, 1);
        createPrediction(bobId, 1);

        var result = finalizeRoundUseCase.execute(seasonId);

        assertThat(result.isRight()).isTrue();
        assertThat(result.get().resultsCalculated()).isEqualTo(2);

        var submissions = roundSubmissionRepo.findBySeasonAndRound(seasonId, 1);
        assertThat(submissions).hasSize(2);

        for (var submission : submissions) {
            var roundResult = roundResultRepo.findByRoundSubmissionId(submission.getId());
            assertThat(roundResult).isPresent();
            assertThat(roundResult.get().getTotalScore()).isGreaterThanOrEqualTo(0);
        }
    }

    @Test
    @DisplayName("Should snapshot submission rankings from currentRankings (not deprecated initialRankings)")
    void shouldSnapshotSubmissionRankingsFromCurrentRankings() throws Exception {
        insertSeason(2, 4);

        Round round1 = createRound(1, false);
        createRound(2, false);
        setCurrentRound(round1.getId());

        createFinishedMatch(round1, arsenal, chelsea, 2, 1);
        createFinishedMatch(round1, liverpool, manCity, 1, 1);

        List<TeamRank> currentOnly =
                List.of(TeamRank.of("MCI", 1), TeamRank.of("LIV", 2), TeamRank.of("CHE", 3), TeamRank.of("ARS", 4));
        createPredictionWithEmptyInitial(aliceId, 1, currentOnly);

        var result = finalizeRoundUseCase.execute(seasonId);
        assertThat(result.isRight()).isTrue();

        var submissions = roundSubmissionRepo.findBySeasonAndRound(seasonId, 1);
        assertThat(submissions).hasSize(1);
        assertThat(submissions.get(0).getRankings()).isEqualTo(currentOnly);
    }

    @Test
    @DisplayName("Should finalize multiple rounds sequentially")
    void shouldFinalizeMultipleRoundsSequentially() throws Exception {
        insertSeason(3, 4);

        Round round1 = createRound(1, false);
        Round round2 = createRound(2, false);
        Round round3 = createRound(3, false);
        setCurrentRound(round1.getId());

        // Round 1 matches
        createFinishedMatch(round1, arsenal, chelsea, 2, 0);
        createFinishedMatch(round1, liverpool, manCity, 1, 1);

        // Round 2 matches
        createFinishedMatch(round2, chelsea, liverpool, 3, 0);
        createFinishedMatch(round2, manCity, arsenal, 2, 1);

        // Round 3 matches
        createFinishedMatch(round3, arsenal, liverpool, 2, 0);
        createFinishedMatch(round3, chelsea, manCity, 1, 1);

        createPrediction(aliceId, 1);

        var result1 = finalizeRoundUseCase.execute(seasonId);
        assertThat(result1.isRight()).isTrue();
        roundAdvancementService.advanceManually(result1.get().roundId());

        var result2 = finalizeRoundUseCase.execute(seasonId);
        assertThat(result2.isRight()).isTrue();
        roundAdvancementService.advanceManually(result2.get().roundId());

        var result3 = finalizeRoundUseCase.execute(seasonId);
        assertThat(result3.isRight()).isTrue();
        roundAdvancementService.advanceManually(result3.get().roundId());

        assertThat(standingsRepo.findBySeasonAndRoundPosition(seasonId, 1)).isPresent();
        assertThat(standingsRepo.findBySeasonAndRoundPosition(seasonId, 2)).isPresent();
        assertThat(standingsRepo.findBySeasonAndRoundPosition(seasonId, 3)).isPresent();

        // RoundAdvancementService no longer auto-completes the season on the last round advancing
        // (that's now a separate, explicit admin action) — it just marks the round advanced.
        assertThat(roundRepo.findById(round3.getId()).orElseThrow().isAdvanced())
                .isTrue();
        var season = seasonRepo.findById(seasonId).orElseThrow();
        assertThat(season.isCompleted()).isFalse();
    }

    @Test
    @DisplayName("Should detect season completion when finalizing last round")
    void shouldDetectSeasonCompletion() throws Exception {
        insertSeason(1, 4);

        Round round1 = createRound(1, false);
        setCurrentRound(round1.getId());

        createFinishedMatch(round1, arsenal, chelsea, 2, 1);
        createPrediction(aliceId, 1);

        var result = finalizeRoundUseCase.execute(seasonId);

        assertThat(result.isRight()).isTrue();
        assertThat(result.get().seasonCompleted()).isTrue();

        // FinalizeRoundUseCase no longer completes the season; that happens on explicit advance.
        var season = seasonRepo.findById(seasonId).orElseThrow();
        assertThat(season.isCompleted()).isFalse();
    }

    @Test
    @DisplayName("Should handle finalization when no users submitted prediction")
    void shouldHandleFinalizationWhenNoSubmissions() throws Exception {
        insertSeason(2, 4);

        Round round1 = createRound(1, false);
        createRound(2, false);
        setCurrentRound(round1.getId());

        createFinishedMatch(round1, arsenal, chelsea, 2, 1);
        createFinishedMatch(round1, liverpool, manCity, 1, 1);

        var result = finalizeRoundUseCase.execute(seasonId);

        assertThat(result.isRight()).isTrue();
        assertThat(result.get().submissionsCreated()).isEqualTo(0);
        assertThat(result.get().resultsCalculated()).isEqualTo(0);

        assertThat(standingsRepo.findBySeasonAndRoundPosition(seasonId, 1)).isPresent();
    }

    @Test
    @DisplayName("Should calculate cumulative standings across multiple rounds")
    void shouldCalculateCumulativeStandings() throws Exception {
        insertSeason(2, 4);

        Round round1 = createRound(1, false);
        Round round2 = createRound(2, false);
        setCurrentRound(round1.getId());

        createFinishedMatch(round1, arsenal, chelsea, 2, 0);
        createFinishedMatch(round1, liverpool, manCity, 1, 1);

        createFinishedMatch(round2, chelsea, liverpool, 3, 0);
        createFinishedMatch(round2, manCity, arsenal, 2, 1);

        createPrediction(aliceId, 1);

        assertThat(finalizeRoundUseCase.execute(seasonId).isRight()).isTrue();
        roundAdvancementService.advanceManually(round1.getId());

        var result2 = finalizeRoundUseCase.execute(seasonId);
        assertThat(result2.isRight()).isTrue();

        Standings standings =
                standingsRepo.findBySeasonAndRoundPosition(seasonId, 2).orElseThrow();

        // Man City: draw in round 1 (1pt), win in round 2 (3pt) => 4pt total
        var manCityRank = standings.findByTeamCode("MCI").orElseThrow();
        assertThat(manCityRank.getMetadata().getPlayed()).isEqualTo(2);
        assertThat(manCityRank.getMetadata().getPoints()).isEqualTo(4);
    }

    @Test
    @DisplayName("Should be idempotent for last round - calling twice should return AlreadyFinalized")
    void shouldBeIdempotent() throws Exception {
        insertSeason(1, 4);

        Round round1 = createRound(1, false);
        setCurrentRound(round1.getId());

        createFinishedMatch(round1, arsenal, chelsea, 2, 1);
        createPrediction(aliceId, 1);

        var firstResult = finalizeRoundUseCase.execute(seasonId);
        assertThat(firstResult.isRight()).isTrue();

        var secondResult = finalizeRoundUseCase.execute(seasonId);
        assertThat(secondResult.isLeft()).isTrue();
        assertThat(secondResult.getLeft()).isInstanceOf(FinalizeRoundError.AlreadyFinalized.class);

        // no duplicate standings
        assertThat(standingsRepo.findBySeasonAndRoundPosition(seasonId, 1)).isPresent();

        var submissions = roundSubmissionRepo.findBySeasonAndRound(seasonId, 1);
        assertThat(submissions).hasSize(1);
        assertThat(roundResultRepo.findByRoundSubmissionId(submissions.get(0).getId()))
                .isPresent();
    }

    @Test
    @DisplayName("Should allow recompute after round is finalized")
    void shouldAllowRecomputeAfterFinalized() throws Exception {
        insertSeason(1, 4);

        Round round1 = createRound(1, false);
        setCurrentRound(round1.getId());

        createFinishedMatch(round1, arsenal, chelsea, 2, 1);
        createPrediction(aliceId, 1);

        var first = finalizeRoundUseCase.execute(new FinalizeRoundCommand(seasonId, null, false));
        assertThat(first.isRight()).isTrue();

        var second = finalizeRoundUseCase.execute(new FinalizeRoundCommand(seasonId, null, true));
        assertThat(second.isRight()).isTrue();

        // No duplicates; recompute overwrites results if needed.
        var submissions = roundSubmissionRepo.findBySeasonAndRound(seasonId, 1);
        assertThat(submissions).hasSize(1);
        assertThat(roundResultRepo.findByRoundSubmissionId(submissions.get(0).getId()))
                .isPresent();
    }

    @Test
    @DisplayName("Should reject an explicit refinalize when the season is not in setup mode")
    void shouldRejectRefinalize_whenSeasonNotInSetupMode() throws Exception {
        insertSeason(2, 4);

        Round round1 = createRound(1, false);
        createRound(2, false);
        setCurrentRound(round1.getId());

        createFinishedMatch(round1, arsenal, chelsea, 2, 1);
        assertThat(finalizeRoundUseCase.execute(seasonId).isRight()).isTrue();

        // Attach a main contest so the season is no longer in setup mode.
        insertContest(UUID.randomUUID());

        var result = finalizeRoundUseCase.execute(FinalizeRoundCommand.refinalize(seasonId, 1));

        assertThat(result.isLeft()).isTrue();
        assertThat(result.getLeft()).isInstanceOf(FinalizeRoundError.NotInSetupMode.class);
    }

    @Test
    @DisplayName("Should reject an explicit refinalize targeting a round ahead of the current round")
    void shouldRejectRefinalize_whenRoundAheadOfCurrent() throws Exception {
        insertSeason(3, 4);

        Round round1 = createRound(1, false);
        createRound(2, false);
        createRound(3, false);
        setCurrentRound(round1.getId());

        createFinishedMatch(round1, arsenal, chelsea, 2, 1);
        assertThat(finalizeRoundUseCase.execute(seasonId).isRight()).isTrue();

        // current round is still round 1 (never advanced); round 2 is ahead of it.
        var result = finalizeRoundUseCase.execute(FinalizeRoundCommand.refinalize(seasonId, 2));

        assertThat(result.isLeft()).isTrue();
        assertThat(result.getLeft()).isInstanceOf(FinalizeRoundError.RoundAheadOfCurrent.class);
        var error = (FinalizeRoundError.RoundAheadOfCurrent) result.getLeft();
        assertThat(error.roundPosition()).isEqualTo(2);
        assertThat(error.currentRoundPosition()).isEqualTo(1);

        // no side effects — round 1 finalization state is untouched
        assertThat(roundRepo.findById(round1.getId()).orElseThrow().isFinalized())
                .isTrue();
    }

    @Test
    @DisplayName("Refinalizing a past round marks downstream rounds (through current, inclusive) unfinalized")
    void shouldCascadeOutOfSync_whenRefinalizingPastRound() throws Exception {
        insertSeason(3, 4);

        Round round1 = createRound(1, false);
        Round round2 = createRound(2, false);
        Round round3 = createRound(3, false);
        setCurrentRound(round1.getId());

        createFinishedMatch(round1, arsenal, chelsea, 2, 0);
        createFinishedMatch(round2, chelsea, liverpool, 3, 0);
        createFinishedMatch(round3, arsenal, liverpool, 2, 0);

        assertThat(finalizeRoundUseCase.execute(seasonId).isRight()).isTrue();
        roundAdvancementService.advanceManually(round1.getId());

        assertThat(finalizeRoundUseCase.execute(seasonId).isRight()).isTrue();
        roundAdvancementService.advanceManually(round2.getId());

        assertThat(finalizeRoundUseCase.execute(seasonId).isRight()).isTrue();

        // season is left in setup mode by default (insertSeason never sets fk_main_contest_id)
        var refinalizeResult = finalizeRoundUseCase.execute(FinalizeRoundCommand.refinalize(seasonId, 1));
        assertThat(refinalizeResult.isRight()).isTrue();

        assertThat(roundRepo.findById(round1.getId()).orElseThrow().isFinalized())
                .isTrue();
        assertThat(roundRepo.findById(round2.getId()).orElseThrow().isFinalized())
                .isFalse();
        assertThat(roundRepo.findById(round3.getId()).orElseThrow().isFinalized())
                .isFalse();

        assertThat(standingsRepo
                        .findBySeasonAndRoundPosition(seasonId, 1)
                        .orElseThrow()
                        .isFinalised())
                .isTrue();
        var standings2 = standingsRepo.findBySeasonAndRoundPosition(seasonId, 2).orElseThrow();
        assertThat(standings2.isFinalised()).isFalse();
        assertThat(standings2.getFinalisedAt()).isNull();
        var standings3 = standingsRepo.findBySeasonAndRoundPosition(seasonId, 3).orElseThrow();
        assertThat(standings3.isFinalised()).isFalse();
        assertThat(standings3.getFinalisedAt()).isNull();
    }

    @Test
    @DisplayName("Refinalizing the current round does not cascade")
    void shouldNotCascade_whenRefinalizingCurrentRound() throws Exception {
        insertSeason(2, 4);

        Round round1 = createRound(1, false);
        Round round2 = createRound(2, false);
        setCurrentRound(round1.getId());

        createFinishedMatch(round1, arsenal, chelsea, 2, 0);
        createFinishedMatch(round2, chelsea, liverpool, 3, 0);

        assertThat(finalizeRoundUseCase.execute(seasonId).isRight()).isTrue();
        roundAdvancementService.advanceManually(round1.getId());
        assertThat(finalizeRoundUseCase.execute(seasonId).isRight()).isTrue();

        // round2 is now the current round; refinalizing it should not touch round1.
        var result = finalizeRoundUseCase.execute(FinalizeRoundCommand.refinalize(seasonId, 2));
        assertThat(result.isRight()).isTrue();

        assertThat(roundRepo.findById(round1.getId()).orElseThrow().isFinalized())
                .isTrue();
        assertThat(roundRepo.findById(round2.getId()).orElseThrow().isFinalized())
                .isTrue();
        assertThat(standingsRepo
                        .findBySeasonAndRoundPosition(seasonId, 1)
                        .orElseThrow()
                        .isFinalised())
                .isTrue();
    }

    private void insertContest(UUID id) {
        jdbc.update(
                "INSERT INTO t_contest (pk_id, fk_season_id, c_name, c_is_private, c_join_code, c_from_round_position, c_to_round_position, c_max_entries) VALUES (?,?,?,?,?,?,?,?)",
                id,
                seasonId,
                "Main",
                false,
                null,
                1,
                38,
                null);
        jdbc.update("UPDATE t_season SET fk_main_contest_id = ? WHERE pk_id = ?", id, seasonId);
    }

    private void insertCompetition() {
        jdbc.update(
                "INSERT INTO t_competition (pk_id, c_name, c_slug, c_code, fk_active_season_id) VALUES (?,?,?,?,?)",
                competitionId,
                "Premier League",
                "premier-league",
                "PL",
                null);
    }

    private void insertSeason(int maxRounds, int totalTeams) throws Exception {
        int seasonClientId = ThreadLocalRandom.current().nextInt(1_000, 1_000_000);

        List<TeamRank> initialRankings =
                List.of(TeamRank.of("ARS", 1), TeamRank.of("CHE", 2), TeamRank.of("LIV", 3), TeamRank.of("MCI", 4));

        String initialRankingsJson = objectMapper.writeValueAsString(initialRankings);

        jdbc.update(
                "INSERT INTO t_season (pk_id, c_client_id, fk_competition_id, c_name, c_slug, c_start_date, c_end_date, c_max_rounds, c_completed, c_completed_at, c_total_teams, c_max_hit_points, c_initial_rankings, fk_main_contest_id, fk_current_round_id, c_current_match_day) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?::jsonb,?,?,?)",
                seasonId,
                seasonClientId,
                competitionId,
                "2024/25",
                "2024-25",
                LocalDate.of(2024, 8, 1),
                LocalDate.of(2025, 5, 31),
                maxRounds,
                false,
                null,
                totalTeams,
                220,
                initialRankingsJson,
                null,
                null,
                1);
    }

    private void setCurrentRound(UUID roundId) {
        jdbc.update("UPDATE t_season SET fk_current_round_id = ? WHERE pk_id = ?", roundId, seasonId);
    }

    private Team createTeam(String code, String name) {
        Team team = Team.builder()
                .clientId(10_000 + Math.abs(code.hashCode() % 10_000))
                .name(name)
                .shortName(name)
                .slug(TeamSlug.of(name.toLowerCase().replace(" ", "-")))
                .tla(code)
                .build();
        return teamRepo.create(team);
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

    private Match createFinishedMatch(Round round, Team home, Team away, int hg, int ag) {
        return matchRepo.save(Match.builder()
                .clientId(ThreadLocalRandom.current().nextInt(1_000, 1_000_000))
                .seasonId(seasonId)
                .roundId(round.getId())
                .homeTeamId(home.getId())
                .awayTeamId(away.getId())
                .kickOff(OffsetDateTime.now(ZoneOffset.UTC).minusHours(2))
                .matchday(round.getPosition())
                .slug(home.getCode().toLowerCase() + "-vs-" + away.getCode().toLowerCase() + "-" + round.getPosition())
                .status(MatchStatus.FINISHED)
                .score(Score.builder().homeGoals(hg).awayGoals(ag).build())
                .build());
    }

    private Match createCancelledMatch(Round round, Team home, Team away) {
        return matchRepo.save(Match.builder()
                .clientId(ThreadLocalRandom.current().nextInt(1_000, 1_000_000))
                .seasonId(seasonId)
                .roundId(round.getId())
                .homeTeamId(home.getId())
                .awayTeamId(away.getId())
                .kickOff(OffsetDateTime.now(ZoneOffset.UTC))
                .matchday(round.getPosition())
                .slug(home.getCode().toLowerCase() + "-vs-" + away.getCode().toLowerCase() + "-" + round.getPosition()
                        + "-cancelled")
                .status(MatchStatus.CANCELLED)
                .score(null)
                .build());
    }

    private Match createScheduledMatch(Round round, Team home, Team away) {
        return matchRepo.save(Match.builder()
                .clientId(ThreadLocalRandom.current().nextInt(1_000, 1_000_000))
                .seasonId(seasonId)
                .roundId(round.getId())
                .homeTeamId(home.getId())
                .awayTeamId(away.getId())
                .kickOff(OffsetDateTime.now(ZoneOffset.UTC).plusHours(2))
                .matchday(round.getPosition())
                .slug(home.getCode().toLowerCase() + "-vs-" + away.getCode().toLowerCase() + "-" + round.getPosition()
                        + "-scheduled")
                .status(MatchStatus.SCHEDULED)
                .score(null)
                .build());
    }

    private void createPrediction(UUID userId, int atRoundNumber) {
        List<TeamRank> rankings =
                List.of(TeamRank.of("ARS", 1), TeamRank.of("CHE", 2), TeamRank.of("LIV", 3), TeamRank.of("MCI", 4));

        seasonPredictionRepo.save(SeasonPrediction.builder()
                .userId(userId)
                .seasonId(seasonId)
                .initialRankings(rankings)
                .currentRankings(rankings)
                .atRoundNumber(atRoundNumber)
                .build());
    }

    private void createPredictionWithEmptyInitial(UUID userId, int atRoundNumber, List<TeamRank> currentRankings) {
        seasonPredictionRepo.save(SeasonPrediction.builder()
                .userId(userId)
                .seasonId(seasonId)
                .initialRankings(List.of())
                .currentRankings(currentRankings)
                .atRoundNumber(atRoundNumber)
                .build());
    }

    private UUID insertUser(String email, String displayName) {
        UUID userId = UUID.randomUUID();
        jdbc.update(
                "INSERT INTO t_user (pk_id, c_email, c_password_hash, c_display_name, c_public_id, c_email_verified) VALUES (?,?,?,?,?,?)",
                userId,
                email,
                "test-password-hash",
                displayName,
                randomPublicId(),
                true);
        jdbc.update("INSERT INTO t_user_role (fk_user_id, c_role) VALUES (?, ?)", userId, "PLAYER");
        return userId;
    }
}
