package com.ligitabl.api.rest.demoseeding;

import static com.ligitabl.api.rest.demoseeding.SeedingTestFixtures.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.*;
import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.ligitabl.api.rest.round.finalizeround.AdvanceCurrentRoundUseCase;
import com.ligitabl.api.rest.round.finalizeround.FinalizeRoundError;
import com.ligitabl.api.rest.round.finalizeround.FinalizeRoundUseCase;
import com.ligitabl.api.runners.demoseeding.*;
import com.ligitabl.api.shared.Either;
import com.ligitabl.model.auth.Email;
import com.ligitabl.model.auth.PublicId;
import com.ligitabl.model.auth.Role;
import com.ligitabl.model.domain.*;
import com.ligitabl.model.repo.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("SeedSeasonUseCase")
class SeedSeasonUseCaseTest {

    @Mock
    private SeedingConfigLoader configLoader;

    @Mock
    private CompetitionRepo competitionRepo;

    @Mock
    private SeasonRepo seasonRepo;

    @Mock
    private RoundRepo roundRepo;

    @Mock
    private TeamRepo teamRepo;

    @Mock
    private ContestRepo contestRepo;

    @Mock
    private UserRepo userRepo;

    @Mock
    private MatchRepo matchRepo;

    @Mock
    private SeasonPredictionRepo predictionRepo;

    @Mock
    private EntryRepo entryRepo;

    @Mock
    private StandingsRepo standingsRepo;

    @Mock
    private FinalizeRoundUseCase finalizeRoundUseCase;

    @Mock
    private AdvanceCurrentRoundUseCase advanceCurrentRoundUseCase;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private Clock clock;

    @InjectMocks
    private SeedSeasonUseCase useCase;

    private SeedingConfig validConfig;
    private Competition competition;
    private Season season;
    private List<Round> rounds;
    private List<Team> teams;
    private Contest defaultContest;

    @BeforeEach
    void setUp() {
        // Setup fixed clock
        lenient().when(clock.instant()).thenReturn(Instant.parse("2024-12-25T10:00:00Z"));
        lenient().when(clock.getZone()).thenReturn(ZoneId.of("UTC"));

        // Setup test data
        validConfig = createValidSeedingConfig();
        competition = createCompetition();
        season = createSeason(competition);
        rounds = createRounds(season, 22);
        teams = createTeams(12);
        defaultContest = createContest(season);

        // SeedSeasonUseCase resolves the default contest via season.mainContestId
        season.setMainContestId(defaultContest.getId());

        lenient()
                .when(advanceCurrentRoundUseCase.execute(any()))
                .thenReturn(Either.right(new AdvanceCurrentRoundUseCase.AdvanceCurrentRoundResult(true, false, 2)));
    }

    @Nested
    @DisplayName("Success Cases")
    class SuccessCases {

        @Test
        @DisplayName("should seed season successfully with all prerequisites met")
        void shouldSeedSuccessfully() {
            // Arrange
            setupSuccessfulMocks();

            // Act
            Either<SeedingError, SeasonSeedResult> result = useCase.execute();

            // Assert
            assertThat(result.isRight()).isTrue();

            SeasonSeedResult seedResult = result.get();
            assertThat(seedResult.getSeason()).isEqualTo(season);
            assertThat(seedResult.getUsers()).hasSize(3);
            assertThat(seedResult.getDefaultContest()).isEqualTo(defaultContest);
            assertThat(seedResult.getTotalRounds()).isEqualTo(22);
            assertThat(seedResult.getMatchesSeeded()).isGreaterThan(0);

            // Verify interactions
            verify(competitionRepo).findBySlug("premier-league");
            verify(seasonRepo).findBySlug(competition.getId(), "2024-25");
            verify(matchRepo, atLeastOnce()).save(any(Match.class));
            verify(predictionRepo, times(validConfig.getDemoUsers().size() * 2)).save(any(SeasonPrediction.class));
        }

        @Test
        @DisplayName("should create matches for all rounds")
        void shouldCreateMatchesForAllRounds() {
            // Arrange
            setupSuccessfulMocks();
            when(matchRepo.existsBySeasonAndRoundAndTeams(
                            any(UUID.class), any(UUID.class), any(UUID.class), any(UUID.class)))
                    .thenReturn(false);

            // Act
            Either<SeedingError, SeasonSeedResult> result = useCase.execute();

            // Assert
            assertThat(result.isRight()).isTrue();

            int expectedMatches = 22 * 6; // 22 rounds * 6 matches per round (12 teams)
            verify(matchRepo, times(expectedMatches)).save(any(Match.class));
        }

        @Test
        @DisplayName("should skip existing matches")
        void shouldSkipExistingMatches() {
            // Arrange
            setupSuccessfulMocks();
            when(matchRepo.existsBySeasonAndRoundAndTeams(
                            any(UUID.class), any(UUID.class), any(UUID.class), any(UUID.class)))
                    .thenReturn(true); // All matches exist

            // Act
            Either<SeedingError, SeasonSeedResult> result = useCase.execute();

            // Assert
            assertThat(result.isRight()).isTrue();
            assertThat(result.get().getMatchesSeeded()).isEqualTo(0);
            verify(matchRepo, never()).save(any(Match.class));
        }

        @Test
        @DisplayName("should create prediction for all users")
        void shouldCreatePredictionsForAllUsers() {
            // Arrange
            setupSuccessfulMocks();

            // Act
            Either<SeedingError, SeasonSeedResult> result = useCase.execute();

            // Assert
            assertThat(result.isRight()).isTrue();
            verify(predictionRepo, times(validConfig.getDemoUsers().size() * 2)).save(any(SeasonPrediction.class));
            verify(entryRepo, times(3)).save(any(Entry.class));
        }

        @Test
        @DisplayName("should finalize specified number of rounds")
        void shouldFinalizeRounds() {
            // Arrange
            setupSuccessfulMocks();
            when(finalizeRoundUseCase.execute(any(UUID.class))).thenReturn(Either.right(createFinalizationResult()));

            // Act
            Either<SeedingError, SeasonSeedResult> result = useCase.execute();

            // Assert
            assertThat(result.isRight()).isTrue();
            assertThat(result.get().getRoundsFinalized()).isEqualTo(18);
            verify(finalizeRoundUseCase, times(18)).execute(season.getId());
        }

        @Test
        @DisplayName("should handle partial finalization with warnings")
        void shouldHandlePartialFinalization() {
            // Arrange
            setupSuccessfulMocks();

            // First 10 rounds succeed, then fail
            when(finalizeRoundUseCase.execute(any(UUID.class)))
                    .thenReturn(Either.right(createFinalizationResult()))
                    .thenReturn(Either.right(createFinalizationResult()))
                    .thenReturn(Either.right(createFinalizationResult()))
                    .thenReturn(Either.right(createFinalizationResult()))
                    .thenReturn(Either.right(createFinalizationResult()))
                    .thenReturn(Either.right(createFinalizationResult()))
                    .thenReturn(Either.right(createFinalizationResult()))
                    .thenReturn(Either.right(createFinalizationResult()))
                    .thenReturn(Either.right(createFinalizationResult()))
                    .thenReturn(Either.right(createFinalizationResult()))
                    .thenReturn(Either.left(
                            new FinalizeRoundError.RoundNotReady(UUID.randomUUID(), "Matches not finished")));

            // Act
            Either<SeedingError, SeasonSeedResult> result = useCase.execute();

            // Assert
            assertThat(result.isRight()).isTrue();
            assertThat(result.get().getRoundsFinalized()).isEqualTo(10);
            assertThat(result.get().getWarnings()).isNotEmpty();
            assertThat(result.get().getWarnings().get(0)).contains("not ready");
        }
    }

    @Nested
    @DisplayName("Error Cases")
    class ErrorCases {

        @Test
        @DisplayName("should fail when configuration cannot be loaded")
        void shouldFailOnConfigurationError() {
            // Arrange
            when(configLoader.loadConfig()).thenThrow(new RuntimeException("YAML parse error"));

            // Act
            Either<SeedingError, SeasonSeedResult> result = useCase.execute();

            // Assert
            assertThat(result.isLeft()).isTrue();
            assertThat(result.getLeft()).isInstanceOf(SeedingError.ConfigurationError.class);
            assertThat(result.getLeft().message()).contains("YAML parse error");
        }

        @Test
        @DisplayName("should fail when competition not found")
        void shouldFailWhenCompetitionNotFound() {
            // Arrange
            when(configLoader.loadConfig()).thenReturn(validConfig);
            when(competitionRepo.findBySlug("premier-league")).thenReturn(Optional.empty());

            // Act
            Either<SeedingError, SeasonSeedResult> result = useCase.execute();

            // Assert
            assertThat(result.isLeft()).isTrue();
            assertThat(result.getLeft()).isInstanceOf(SeedingError.CompetitionNotFound.class);

            SeedingError.CompetitionNotFound error = (SeedingError.CompetitionNotFound) result.getLeft();
            assertThat(error.slug()).isEqualTo("premier-league");
        }

        @Test
        @DisplayName("should fail when season not found")
        void shouldFailWhenSeasonNotFound() {
            // Arrange
            when(configLoader.loadConfig()).thenReturn(validConfig);
            when(competitionRepo.findBySlug("premier-league")).thenReturn(Optional.of(competition));
            when(seasonRepo.findBySlug(competition.getId(), "2024-25")).thenReturn(Optional.empty());

            // Act
            Either<SeedingError, SeasonSeedResult> result = useCase.execute();

            // Assert
            assertThat(result.isLeft()).isTrue();
            assertThat(result.getLeft()).isInstanceOf(SeedingError.SeasonNotFound.class);
        }

        @Test
        @DisplayName("should fail when wrong number of rounds")
        void shouldFailWhenWrongNumberOfRounds() {
            // Arrange
            when(configLoader.loadConfig()).thenReturn(validConfig);
            when(competitionRepo.findBySlug(anyString())).thenReturn(Optional.of(competition));
            when(seasonRepo.findBySlug(eq(competition.getId()), anyString())).thenReturn(Optional.of(season));
            when(roundRepo.findBySeasonIdOrderByPosition(any(UUID.class)))
                    .thenReturn(createRounds(season, 10)); // Wrong count

            // Act
            Either<SeedingError, SeasonSeedResult> result = useCase.execute();

            // Assert
            assertThat(result.isLeft()).isTrue();
            assertThat(result.getLeft()).isInstanceOf(SeedingError.RoundsNotFound.class);

            SeedingError.RoundsNotFound error = (SeedingError.RoundsNotFound) result.getLeft();
            assertThat(error.expected()).isEqualTo(22);
            assertThat(error.found()).isEqualTo(10);
        }

        @Test
        @DisplayName("should fail when team not found in database")
        void shouldFailWhenTeamNotFound() {
            // Arrange
            when(configLoader.loadConfig()).thenReturn(validConfig);
            when(competitionRepo.findBySlug(anyString())).thenReturn(Optional.of(competition));
            when(seasonRepo.findBySlug(eq(competition.getId()), anyString())).thenReturn(Optional.of(season));
            when(roundRepo.findBySeasonIdOrderByPosition(any(UUID.class))).thenReturn(rounds);
            when(teamRepo.findByCode("MCI")).thenReturn(Optional.empty()); // First team missing

            // Act
            Either<SeedingError, SeasonSeedResult> result = useCase.execute();

            // Assert
            assertThat(result.isLeft()).isTrue();
            assertThat(result.getLeft()).isInstanceOf(SeedingError.TeamNotFound.class);

            SeedingError.TeamNotFound error = (SeedingError.TeamNotFound) result.getLeft();
            assertThat(error.code()).isEqualTo("MCI");
        }

        @Test
        @DisplayName("should fail when default contest not found")
        void shouldFailWhenDefaultContestNotFound() {
            // Arrange
            setupBasicMocks();
            when(contestRepo.findById(any(UUID.class))).thenReturn(Optional.empty());

            // Act
            Either<SeedingError, SeasonSeedResult> result = useCase.execute();

            // Assert
            assertThat(result.isLeft()).isTrue();
            assertThat(result.getLeft()).isInstanceOf(SeedingError.DefaultContestNotFound.class);
        }
    }

    @Nested
    @DisplayName("Parameterized Error Tests")
    class ParameterizedErrorTests {

        @ParameterizedTest
        @MethodSource("provideErrorScenarios")
        @DisplayName("should handle various error scenarios correctly")
        void shouldHandleErrorScenarios(
                String scenario, MockSetup mockSetup, Class<? extends SeedingError> expectedErrorType) {
            // Arrange
            mockSetup.setup(SeedSeasonUseCaseTest.this);

            // Act
            Either<SeedingError, SeasonSeedResult> result = useCase.execute();

            // Assert
            assertThat(result.isLeft()).as("Scenario: %s should fail", scenario).isTrue();
            assertThat(result.getLeft())
                    .as("Scenario: %s should return correct error type", scenario)
                    .isInstanceOf(expectedErrorType);
        }

        static Stream<Arguments> provideErrorScenarios() {
            return Stream.of(
                    Arguments.of(
                            "Config loading fails",
                            (MockSetup) test ->
                                    when(test.configLoader.loadConfig()).thenThrow(new RuntimeException("Parse error")),
                            SeedingError.ConfigurationError.class),
                    Arguments.of(
                            "Competition not found",
                            (MockSetup) test -> {
                                when(test.configLoader.loadConfig()).thenReturn(test.validConfig);
                                when(test.competitionRepo.findBySlug(anyString()))
                                        .thenReturn(Optional.empty());
                            },
                            SeedingError.CompetitionNotFound.class),
                    Arguments.of(
                            "Season not found",
                            (MockSetup) test -> {
                                when(test.configLoader.loadConfig()).thenReturn(test.validConfig);
                                when(test.competitionRepo.findBySlug(anyString()))
                                        .thenReturn(Optional.of(test.competition));
                                when(test.seasonRepo.findBySlug(eq(test.competition.getId()), anyString()))
                                        .thenReturn(Optional.empty());
                            },
                            SeedingError.SeasonNotFound.class),
                    Arguments.of(
                            "No rounds found",
                            (MockSetup) test -> {
                                when(test.configLoader.loadConfig()).thenReturn(test.validConfig);
                                when(test.competitionRepo.findBySlug(anyString()))
                                        .thenReturn(Optional.of(test.competition));
                                when(test.seasonRepo.findBySlug(eq(test.competition.getId()), anyString()))
                                        .thenReturn(Optional.of(test.season));
                                when(test.roundRepo.findBySeasonIdOrderByPosition(any(UUID.class)))
                                        .thenReturn(Collections.emptyList());
                            },
                            SeedingError.NoRoundsFound.class));
        }

        @FunctionalInterface
        interface MockSetup {
            void setup(SeedSeasonUseCaseTest test);
        }
    }

    // Helper methods

    private void setupSuccessfulMocks() {
        setupBasicMocks();
        lenient().when(contestRepo.findById(any(UUID.class))).thenReturn(Optional.of(defaultContest));
        lenient()
                .when(matchRepo.existsBySeasonAndRoundAndTeams(
                        any(UUID.class), any(UUID.class), any(UUID.class), any(UUID.class)))
                .thenReturn(false);

        // SeedSeasonUseCase requires demo users to already exist
        for (SeedingConfig.DemoUser demoUser : validConfig.getDemoUsers()) {
            Email email = Email.create(demoUser.getEmail());
            User user = User.builder()
                    .id(UUID.randomUUID())
                    .publicId(PublicId.create("AbCd3fGh9J"))
                    .email(email)
                    .displayName(demoUser.getDisplayName())
                    .roles(Set.of(Role.PLAYER))
                    .emailVerified(true)
                    .build();
            lenient().when(userRepo.findByEmail(eq(email))).thenReturn(Optional.of(user));
        }

        lenient()
                .when(predictionRepo.findByUserAndSeason(any(UUID.class), any(UUID.class)))
                .thenReturn(Optional.empty());
        lenient().when(predictionRepo.save(any(SeasonPrediction.class))).thenAnswer(inv -> inv.getArgument(0));
        lenient().when(entryRepo.save(any(Entry.class))).thenAnswer(inv -> inv.getArgument(0));
        lenient().when(matchRepo.save(any(Match.class))).thenAnswer(inv -> inv.getArgument(0));
        lenient()
                .when(matchRepo.findByRoundId(any(UUID.class)))
                .thenAnswer(inv -> createMatches(season.getId(), inv.getArgument(0)));
        lenient()
                .when(standingsRepo.findBySeasonAndRoundPosition(any(UUID.class), anyInt()))
                .thenReturn(Optional.of(createStandings(season.getId())));
        lenient()
                .when(finalizeRoundUseCase.execute(any(UUID.class)))
                .thenReturn(Either.right(createFinalizationResult()));
        lenient().when(passwordEncoder.encode(anyString())).thenReturn("encoded-password");
    }

    private void setupBasicMocks() {
        lenient().when(configLoader.loadConfig()).thenReturn(validConfig);
        lenient().when(competitionRepo.findBySlug("premier-league")).thenReturn(Optional.of(competition));
        lenient().when(seasonRepo.findBySlug(competition.getId(), "2024-25")).thenReturn(Optional.of(season));
        lenient().when(roundRepo.findBySeasonIdOrderByPosition(any(UUID.class))).thenReturn(rounds);

        // Setup team repository to return teams in sequence
        for (Team team : teams) {
            lenient().when(teamRepo.findByCode(team.getCode())).thenReturn(Optional.of(team));
        }
    }
}
