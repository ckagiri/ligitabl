package com.ligitabl.api.usecases.prediction.seeding;

import static org.assertj.core.api.Assertions.*;

import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import com.ligitabl.api.shared.Either;
import com.ligitabl.model.domain.*;
import com.ligitabl.model.repo.*;

@SpringBootTest
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@DisplayName("SeedSeasonUseCase Integration Tests")
class SeedSeasonUseCaseIntegrationTest {

    @Autowired
    private SeedSeasonUseCase seedSeasonUseCase;

    @Autowired
    private CompetitionRepo competitionRepo;

    @Autowired
    private SeasonRepo seasonRepo;

    @Autowired
    private RoundRepo roundRepo;

    @Autowired
    private TeamRepo teamRepo;

    @Autowired
    private ContestRepo contestRepo;

    @Autowired
    private MatchRepo matchRepo;

    @Autowired
    private UserRepo userRepo;

    @Autowired
    private SeasonPredictionRepo predictionRepo;

    private Competition competition;
    private Season season;

    @BeforeAll
    void setupPrerequisites() {
        // Create competition
        competition = Competition.builder()
                .name("Test League")
                .slug("test-league")
                .country("England")
                .build();
        competition = competitionRepo.save(competition);

        // Create teams
        String[] codes = {"MCI", "ARS", "LIV", "AVL", "CHE", "NEW", "MUN", "TOT", "BHA", "CRY", "BRE", "WHU"};

        for (int i = 0; i < codes.length; i++) {
            Team team = Team.builder()
                    .name("Team " + codes[i])
                    .tla(codes[i])
                    .slug("team-" + codes[i].toLowerCase())
                    .logoUrl("https://example.com/" + codes[i] + ".png")
                    .build();
            teamRepo.save(team);
        }

        // Create season
        List<TeamRank> initialRankings = List.of(
                new TeamRank("MCI", 1), new TeamRank("ARS", 2),
                new TeamRank("LIV", 3), new TeamRank("AVL", 4),
                new TeamRank("CHE", 5), new TeamRank("NEW", 6),
                new TeamRank("MUN", 7), new TeamRank("TOT", 8),
                new TeamRank("BHA", 9), new TeamRank("CRY", 10),
                new TeamRank("BRE", 11), new TeamRank("WHU", 12));

        season = Season.builder()
                .name("Test Season 2024/25")
                .slug("test-2024-25")
                .competitionId(competition.getId())
                .startDate(LocalDate.of(2024, 8, 1))
                .endDate(LocalDate.of(2025, 5, 31))
                .maxRounds(22)
                .totalTeams(12)
                .maxHitPoints(220)
                .initialRankings(initialRankings)
                .completed(false)
                .build();
        season = seasonRepo.save(season);

        // Create default contest
        Contest contest = Contest.builder()
                .seasonId(season.getId())
                .name("Test Contest")
                .slug("test-contest")
                .build();
        contest = contestRepo.save(contest);

        season.setMainContestId(contest.getId());
        season = seasonRepo.save(season);

        // Create rounds
        for (int i = 1; i <= 22; i++) {
            Round round = Round.builder()
                    .seasonId(season.getId())
                    .position(i)
                    .name("Round " + i)
                    .build();
            roundRepo.save(round);
        }
    }

    @Test
    @Transactional
    @DisplayName("should seed complete season with real database")
    void shouldSeedCompleteSeasonWithRealDatabase() {
        // Act
        Either<SeedingError, SeasonSeedResult> result = seedSeasonUseCase.execute();

        // Assert - Success
        assertThat(result.isRight()).isTrue();

        SeasonSeedResult seedResult = result.get();
        assertThat(seedResult.getSeason()).isNotNull();
        assertThat(seedResult.getUsers()).hasSize(3);
        assertThat(seedResult.getTotalRounds()).isEqualTo(22);

        // Verify database state
        List<Match> matches = matchRepo.findBySeasonId(season.getId());
        assertThat(matches).isNotEmpty();
        assertThat(matches).hasSizeBetween(100, 264); // At least some matches created

        List<User> users = userRepo.findAll();
        assertThat(users).hasSizeGreaterThanOrEqualTo(3);

        List<SeasonPrediction> predictions = predictionRepo.findBySeasonId(season.getId());
        assertThat(predictions).hasSize(3);

        // Verify predictions have correct structure
        predictions.forEach(prediction -> {
            assertThat(prediction.getInitialRankings()).hasSize(12);
            assertThat(prediction.getCurrentRankings()).hasSize(12);
            assertThat(prediction.getAtRoundNumber()).isGreaterThan(0);
        });
    }

    @Test
    @Transactional
    @DisplayName("should be idempotent - running twice doesn't duplicate data")
    void shouldBeIdempotent() {
        // Act - Run twice
        Either<SeedingError, SeasonSeedResult> result1 = seedSeasonUseCase.execute();
        Either<SeedingError, SeasonSeedResult> result2 = seedSeasonUseCase.execute();

        // Assert - Both succeed
        assertThat(result1.isRight()).isTrue();
        assertThat(result2.isRight()).isTrue();

        // Assert - No duplicate data
        List<User> users = userRepo.findAll();
        assertThat(users).hasSize(3); // Not duplicated

        List<SeasonPrediction> predictions = predictionRepo.findBySeasonId(season.getId());
        assertThat(predictions).hasSize(3); // Not duplicated
    }

    @AfterAll
    void cleanup() {
        // Clean up in reverse order of dependencies
        predictionRepo.deleteAll();
        matchRepo.deleteAll();
        roundRepo.deleteAll();
        contestRepo.deleteAll();
        seasonRepo.deleteAll();
        teamRepo.deleteAll();
        competitionRepo.deleteAll();
        userRepo.deleteAll();
    }
}
