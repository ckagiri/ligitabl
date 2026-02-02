package com.ligitabl.api.usecases.sync;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

import com.ligitabl.api.scheduling.advancematchday.AdvanceMatchdayUseCase;
import com.ligitabl.api.scheduling.syncmatches.SyncMatchesUseCase;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.ligitabl.api.scheduling.syncmatches.AsyncStandingsService;
import com.ligitabl.api.scheduling.syncmatches.MatchSyncScheduler;
import com.ligitabl.api.scheduling.advancematchday.MatchdayAdvancementScheduler;
import com.ligitabl.api.shared.Either;
import com.ligitabl.api.testsupport.AbstractPostgresIT;
import com.ligitabl.api.testsupport.PostgresTestDbCleaner;
import com.ligitabl.model.domain.MatchStatus;
import com.ligitabl.model.repo.MatchRepo;
import com.ligitabl.model.repo.SeasonRepo;

@SpringBootTest
@DisplayName("Match sync + round advancement integration")
class MatchSyncIntegrationTest extends AbstractPostgresIT {

    private static final WireMockServer WIREMOCK =
            new WireMockServer(WireMockConfiguration.options().dynamicPort());

    private static final String COMPETITION_CODE = "PL";

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        if (!WIREMOCK.isRunning()) {
            WIREMOCK.start();
        }
        registry.add("football-data.api.url", WIREMOCK::baseUrl);
        registry.add("football-data.api.token", () -> "test-token");
        registry.add("football-data.competition.code", () -> COMPETITION_CODE);
        // The product config uses `retryAttempts` as the number of retries (total calls = 1 + retries).
        // The sketch expects 3 total requests on error.
        registry.add("football-data.api.retry-attempts", () -> "2");
    }

    @Autowired
    SyncMatchesUseCase syncMatchesUseCase;

    @Autowired
    AdvanceMatchdayUseCase advanceRoundUseCase;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Autowired
    MatchRepo matchRepo;

    @Autowired
    SeasonRepo seasonRepo;

    // Prevent background schedulers from running in @SpringBootTest and hitting WireMock unexpectedly.
    @MockBean
    MatchSyncScheduler matchSyncScheduler;

    @MockBean
    MatchdayAdvancementScheduler roundAdvancementScheduler;

    // Keep tests deterministic; we only care that sync triggers recalculation when matches become finished.
    @MockBean
    AsyncStandingsService asyncStandingsService;

    private Fixture fixture;

    @BeforeEach
    void setup() {
        WIREMOCK.resetAll();
        PostgresTestDbCleaner.truncateAllDomainTables(jdbcTemplate);
        fixture = Fixture.seed(jdbcTemplate);
    }

    @AfterAll
    static void teardown() {
        if (WIREMOCK.isRunning()) {
            WIREMOCK.stop();
        }
    }

    @Test
    @DisplayName("should sync matches from API successfully")
    void shouldSyncMatchesFromApiSuccessfully() {
        stubMatchesApiWithFinishedMatch(fixture.matchClientId(), fixture.apiKickoff());

        Either<SyncMatchesUseCase.SyncMatchesError, ?> result =
                syncMatchesUseCase.execute(new SyncMatchesUseCase.SyncMatchesCommand());

        assertThat(result.isRight()).isTrue();

        var updated = matchRepo.findByRoundId(fixture.roundId());
        assertThat(updated).hasSize(1);
        assertThat(updated.get(0).getStatus()).isEqualTo(MatchStatus.FINISHED);

        WIREMOCK.verify(getRequestedFor(urlPathEqualTo("/matches"))
                .withQueryParam("competitions", equalTo(COMPETITION_CODE))
                .withQueryParam("dateFrom", equalTo(LocalDate.now().toString()))
                .withQueryParam("dateTo", equalTo(LocalDate.now().plusDays(2).toString())));
    }

    @Test
    @DisplayName("should handle API error with retries")
    void shouldHandleApiError() {
        stubMatchesApiWithServerError();

        var result = syncMatchesUseCase.execute(new SyncMatchesUseCase.SyncMatchesCommand());

        assertThat(result.isLeft()).isTrue();

        // 1 initial request + 2 retries = 3 total calls
        WIREMOCK.verify(
                3,
                getRequestedFor(urlPathEqualTo("/matches"))
                        .withQueryParam("competitions", equalTo(COMPETITION_CODE))
                        .withQueryParam("dateFrom", equalTo(LocalDate.now().toString()))
                        .withQueryParam(
                                "dateTo", equalTo(LocalDate.now().plusDays(2).toString())));
    }

    @Test
    @DisplayName("should detect all matches complete and calculate next sync")
    void shouldDetectAllMatchesCompleteAndCalculateNextSync() {
        stubMatchesApiWithFinishedMatch(fixture.matchClientId(), fixture.apiKickoff());

        var result = syncMatchesUseCase.execute(new SyncMatchesUseCase.SyncMatchesCommand());

        assertThat(result.isRight()).isTrue();

        var syncResult = result.get();
        assertThat(syncResult.allMatchesComplete()).isTrue();
        assertThat(syncResult.nextSchedule().delay()).isZero();
    }

    @Test
    @DisplayName("should advance round when API indicates new matchday")
    void shouldAdvanceRoundWhenApiIndicatesNewMatchday() {
        stubCompetitionApiWithCurrentMatchday(2);

        var result = advanceRoundUseCase.execute(new AdvanceMatchdayUseCase.AdvanceMatchdayCommand());

        assertThat(result.isRight()).isTrue();
        assertThat(result.get().advanced()).isTrue();
        assertThat(result.get().previousMatchday()).isEqualTo(1);
        assertThat(result.get().newMatchday()).isEqualTo(2);

        var season = seasonRepo.findById(fixture.seasonId()).orElseThrow();
        assertThat(season.getCurrentMatchDay()).isEqualTo(2);

        WIREMOCK.verify(getRequestedFor(urlPathEqualTo("/competitions/" + COMPETITION_CODE)));
    }

    @Test
    @DisplayName("should not advance when matchday unchanged")
    void shouldNotAdvanceWhenMatchdayUnchanged() {
        stubCompetitionApiWithCurrentMatchday(1);

        var result = advanceRoundUseCase.execute(new AdvanceMatchdayUseCase.AdvanceMatchdayCommand());

        assertThat(result.isRight()).isTrue();
        assertThat(result.get().advanced()).isFalse();

        var season = seasonRepo.findById(fixture.seasonId()).orElseThrow();
        assertThat(season.getCurrentMatchDay()).isEqualTo(1);
    }

    private static void stubMatchesApiWithFinishedMatch(int matchId, OffsetDateTime kickOff) {
        String utcKickoff =
                kickOff.withOffsetSameInstant(ZoneOffset.UTC).toInstant().toString();

        String body =
                """
                        {
                            "filters": {
                                "competitions": "%s"
                            },
                            "resultSet": {
                                "count": 1
                            },
                            "competition": {
                                "id": 2021,
                                "name": "Premier League",
                                "code": "%s",
                                "type": "LEAGUE",
                                "emblem": "https://example.test/emblem.png",
                                "currentSeason": {
                                    "id": 123,
                                    "startDate": "2025-08-01T00:00:00Z",
                                    "endDate": "2026-05-31T00:00:00Z",
                                    "currentMatchday": 1
                                }
                            },
                            "matches": [
                                {
                                    "id": %d,
                                    "utcDate": "%s",
                                    "status": "FINISHED",
                                    "matchday": 1,
                                    "stage": "REGULAR_SEASON",
                                    "homeTeam": {
                                        "id": 57,
                                        "name": "Arsenal FC",
                                        "shortName": "Arsenal",
                                        "tla": "ARS",
                                        "crest": "https://example.test/ars.png"
                                    },
                                    "awayTeam": {
                                        "id": 61,
                                        "name": "Chelsea FC",
                                        "shortName": "Chelsea",
                                        "tla": "CHE",
                                        "crest": "https://example.test/che.png"
                                    },
                                    "score": {
                                        "winner": "HOME_TEAM",
                                        "duration": "REGULAR",
                                        "fullTime": { "home": 2, "away": 1 },
                                        "halfTime": { "home": 1, "away": 0 }
                                    }
                                }
                            ]
                        }
                        """
                        .formatted(COMPETITION_CODE, COMPETITION_CODE, matchId, utcKickoff);

        WIREMOCK.stubFor(get(urlPathEqualTo("/matches"))
                .withQueryParam("competitions", equalTo(COMPETITION_CODE))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(body)));
    }

    private static void stubMatchesApiWithServerError() {
        WIREMOCK.stubFor(
                get(urlPathEqualTo("/matches"))
                        .withQueryParam("competitions", equalTo(COMPETITION_CODE))
                        .willReturn(
                                aResponse()
                                        .withStatus(500)
                                        .withHeader("Content-Type", "application/json")
                                        // Football-Data uses RFC7807-ish error payloads in some cases; keep it minimal.
                                        .withBody(
                                                """
                    {
                      \"message\": \"Internal Server Error\",
                      \"errorCode\": 500
                    }
                    """)));
    }

    private static void stubCompetitionApiWithCurrentMatchday(int matchday) {
        String body =
                """
                        {
                            "id": 2021,
                            "name": "Premier League",
                            "code": "%s",
                            "type": "LEAGUE",
                            "emblem": "https://example.test/emblem.png",
                            "currentSeason": {
                                "id": 123,
                                "startDate": "2025-08-01T00:00:00Z",
                                "endDate": "2026-05-31T00:00:00Z",
                                "currentMatchday": %d
                            }
                        }
                        """
                        .formatted(COMPETITION_CODE, matchday);

        WIREMOCK.stubFor(get(urlPathEqualTo("/competitions/" + COMPETITION_CODE))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(body)));
    }

    private record Fixture(
            UUID competitionId,
            UUID seasonId,
            UUID roundId,
            UUID homeTeamId,
            UUID awayTeamId,
            UUID matchId,
            int matchClientId,
            OffsetDateTime apiKickoff) {

        static Fixture seed(JdbcTemplate jdbc) {
            UUID competitionId = UUID.randomUUID();
            UUID seasonId = UUID.randomUUID();
            UUID roundId = UUID.randomUUID();

            UUID homeTeamId = UUID.randomUUID();
            UUID awayTeamId = UUID.randomUUID();

            UUID matchId = UUID.randomUUID();
            int matchClientId = 1001;

            OffsetDateTime kickOff = OffsetDateTime.now().plusDays(1).withNano(0);

            jdbc.update(
                    "insert into t_team (pk_id, c_client_id, c_name, c_short_name, c_slug, c_tla) values (?, ?, ?, ?, ?, ?)",
                    homeTeamId,
                    57,
                    "Arsenal FC",
                    "Arsenal",
                    "arsenal",
                    "ARS");
            jdbc.update(
                    "insert into t_team (pk_id, c_client_id, c_name, c_short_name, c_slug, c_tla) values (?, ?, ?, ?, ?, ?)",
                    awayTeamId,
                    61,
                    "Chelsea FC",
                    "Chelsea",
                    "chelsea",
                    "CHE");

            jdbc.update(
                    "insert into t_competition (pk_id, c_name, c_slug, c_code, c_phases, fk_active_season_id) values (?, ?, ?, ?, ?::jsonb, ?)",
                    competitionId,
                    "Premier League",
                    "premier-league",
                    COMPETITION_CODE,
                    null,
                    null);

            jdbc.update(
                    "insert into t_season (pk_id, c_client_id, fk_competition_id, c_name, c_slug, c_start_date, c_end_date, c_max_rounds, fk_current_round_id, c_current_match_day, c_initial_rankings) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?::jsonb)",
                    seasonId,
                    2024,
                    competitionId,
                    "2024/25",
                    "2024-25",
                    java.sql.Date.valueOf(LocalDate.now().minusDays(10)),
                    java.sql.Date.valueOf(LocalDate.now().plusDays(200)),
                    38,
                    roundId,
                    1,
                    null);

            jdbc.update(
                    "insert into t_round (pk_id, fk_season_id, c_name, c_slug, c_position, c_is_finalized) values (?, ?, ?, ?, ?, ?)",
                    roundId,
                    seasonId,
                    "Matchday 1",
                    "matchday-1",
                    1,
                    false);

            jdbc.update(
                    "insert into t_match (pk_id, c_client_id, fk_round_id, fk_home_team_id, fk_away_team_id, c_score, c_slug, c_status, c_kick_off, c_venue, c_matchday) values (?, ?, ?, ?, ?, ?::jsonb, ?, ?, ?, ?, ?)",
                    matchId,
                    matchClientId,
                    roundId,
                    homeTeamId,
                    awayTeamId,
                    null,
                    "arsenal-v-chelsea-md1",
                    "SCHEDULED",
                    kickOff,
                    null,
                    1);

            return new Fixture(
                    competitionId, seasonId, roundId, homeTeamId, awayTeamId, matchId, matchClientId, kickOff);
        }
    }
}
