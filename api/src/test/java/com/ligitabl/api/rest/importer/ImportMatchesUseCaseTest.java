package com.ligitabl.api.rest.importer;

import static com.ligitabl.api.shared.Either.right;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.ligitabl.api.runners.importer.ImportMatchesUseCase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.ligitabl.api.runners.importer.event.ImportEventPublisher;
import com.ligitabl.api.runners.importer.footballdata.FootballDataGateway;
import com.ligitabl.api.runners.importer.model.entities.ExternalCompetition;
import com.ligitabl.api.runners.importer.model.entities.ExternalMatch;
import com.ligitabl.api.runners.importer.model.entities.ExternalSeason;
import com.ligitabl.api.runners.importer.model.entities.ExternalTeam;
import com.ligitabl.api.runners.importer.model.entities.ImportSummary;
import com.ligitabl.api.runners.importer.model.errors.ApiError;
import com.ligitabl.api.runners.importer.model.errors.DatabaseError;
import com.ligitabl.api.runners.importer.model.errors.ImportError;
import com.ligitabl.api.runners.importer.model.errors.ValidationError;
import com.ligitabl.api.runners.importer.model.valueobjects.CompetitionCode;
import com.ligitabl.api.runners.importer.model.valueobjects.ExternalId;
import com.ligitabl.api.runners.importer.model.valueobjects.TeamTla;
import com.ligitabl.api.shared.Either;
import com.ligitabl.model.domain.Match;
import com.ligitabl.model.domain.MatchStatus;
import com.ligitabl.model.domain.Round;
import com.ligitabl.model.domain.Score;
import com.ligitabl.model.domain.Season;
import com.ligitabl.model.domain.SeasonSlug;
import com.ligitabl.model.domain.Team;
import com.ligitabl.model.domain.TeamSlug;
import com.ligitabl.model.repo.MatchRepo;
import com.ligitabl.model.repo.RoundRepo;
import com.ligitabl.model.repo.SeasonRepo;
import com.ligitabl.model.repo.TeamRepo;

@ExtendWith(MockitoExtension.class)
@DisplayName("ImportMatchesUseCase")
class ImportMatchesUseCaseTest {

    @Mock
    FootballDataGateway footballDataGateway;

    @Mock
    SeasonRepo seasonRepo;

    @Mock
    TeamRepo teamRepo;

    @Mock
    RoundRepo roundRepo;

    @Mock
    MatchRepo matchRepo;

    @Mock
    ImportEventPublisher eventPublisher;

    ImportMatchesUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new ImportMatchesUseCase(
                footballDataGateway, seasonRepo, teamRepo, roundRepo, matchRepo, eventPublisher);
    }

    private static CompetitionCode comp(String code) {
        return CompetitionCode.of(code).get();
    }

    private static ExternalCompetition competition(String code, int seasonClientId) {
        var season = ExternalSeason.builder()
                .id(ExternalId.of(seasonClientId).get())
                .startDate("2024-08-01")
                .endDate("2025-05-31")
                .currentMatchday(1)
                .build();

        return ExternalCompetition.builder()
                .id(ExternalId.of(2021).get())
                .name("Premier League")
                .code(comp(code))
                .currentSeason(season)
                .build();
    }

    private static Season season(UUID seasonId, int clientId) {
        return Season.builder()
                .id(seasonId)
                .clientId(clientId)
                .competitionId(UUID.randomUUID())
                .name("2024/25")
                .slug(SeasonSlug.of("2024-25"))
                .startDate(LocalDate.of(2024, 8, 1))
                .endDate(LocalDate.of(2025, 5, 31))
                .build();
    }

    private static Team team(UUID id, int clientId, String name, String tla) {
        return Team.builder()
                .id(id)
                .clientId(clientId)
                .name(name)
                .shortName(name)
                .slug(TeamSlug.of(name.toLowerCase().replace(" ", "-")))
                .tla(tla)
                .build();
    }

    private static Round round(UUID id, UUID seasonId, int matchday) {
        return Round.builder()
                .id(id)
                .seasonId(seasonId)
                .name("Matchday " + matchday)
                .slug("md-" + matchday)
                .position(matchday)
                .build();
    }

    private static ExternalMatch externalMatch(int matchClientId, int matchday, int homeClientId, int awayClientId) {
        var home = ExternalTeam.builder()
                .id(ExternalId.of(homeClientId).get())
                .name("Arsenal FC")
                .tla(TeamTla.of("ARS").get())
                .build();

        var away = ExternalTeam.builder()
                .id(ExternalId.of(awayClientId).get())
                .name("Chelsea FC")
                .tla(TeamTla.of("CHE").get())
                .build();

        return ExternalMatch.create(
                        matchClientId,
                        OffsetDateTime.now().withNano(0),
                        MatchStatus.SCHEDULED.name(),
                        matchday,
                        home,
                        away,
                        null,
                        null)
                .get();
    }

    private static ExternalMatch externalMatchWithScore(
            int matchClientId, int matchday, int homeClientId, int awayClientId, int homeGoals, int awayGoals) {

        var home = ExternalTeam.builder()
                .id(ExternalId.of(homeClientId).get())
                .name("Arsenal FC")
                .tla(TeamTla.of("ARS").get())
                .build();

        var away = ExternalTeam.builder()
                .id(ExternalId.of(awayClientId).get())
                .name("Chelsea FC")
                .tla(TeamTla.of("CHE").get())
                .build();

        return ExternalMatch.create(
                        matchClientId,
                        OffsetDateTime.now().withNano(0),
                        MatchStatus.FINISHED.name(),
                        matchday,
                        home,
                        away,
                        homeGoals,
                        awayGoals)
                .get();
    }

    @Test
    @DisplayName("should import new match (create)")
    void shouldImportNewMatch() {
        var code = comp("PL");
        var extCompetition = competition("PL", 2024);
        var extMatch = externalMatch(100, 1, 57, 61);

        UUID seasonId = UUID.randomUUID();
        UUID roundId = UUID.randomUUID();
        UUID homeId = UUID.randomUUID();
        UUID awayId = UUID.randomUUID();

        var dbSeason = season(seasonId, 2024);
        var dbRound = round(roundId, seasonId, 1);
        var dbHome = team(homeId, 57, "Arsenal FC", "ARS");
        var dbAway = team(awayId, 61, "Chelsea FC", "CHE");

        when(footballDataGateway.fetchCompetition(code)).thenReturn(right(extCompetition));
        when(seasonRepo.findByClientId(2024)).thenReturn(Optional.of(dbSeason));
        when(footballDataGateway.fetchMatchesForCompetition(code)).thenReturn(right(List.of(extMatch)));
        when(roundRepo.findBySeasonIdAndPosition(seasonId, 1)).thenReturn(Optional.of(dbRound));
        when(teamRepo.findByClientId(57)).thenReturn(Optional.of(dbHome));
        when(teamRepo.findByClientId(61)).thenReturn(Optional.of(dbAway));
        when(matchRepo.findByClientId(100)).thenReturn(Optional.empty());
        when(matchRepo.create(any(Match.class))).thenAnswer(inv -> inv.getArgument(0));

        Either<ImportError, ImportSummary> result = useCase.execute(code);

        assertThat(result.isRight()).isTrue();
        var summary = result.get();
        assertThat(summary.getTotalMatches()).isEqualTo(1);
        assertThat(summary.getCreated()).isEqualTo(1);
        assertThat(summary.getUpdated()).isEqualTo(0);
        assertThat(summary.getFailed()).isEqualTo(0);
        assertThat(summary.isSuccessful()).isTrue();

        verify(matchRepo).create(any(Match.class));
        verify(matchRepo, never()).update(any(Match.class));
        verify(eventPublisher).publishImportCompleted(any());
    }

    @Test
    @DisplayName("should advance current round when external matchday is higher")
    void shouldAdvanceCurrentRoundWhenExternalMatchdayHigher() {
        var seasonId = UUID.randomUUID();
        var currentRoundId = UUID.randomUUID();
        var nextRoundId = UUID.randomUUID();
        var code = comp("PL");

        var extSeason = ExternalSeason.builder()
                .id(ExternalId.of(2024).get())
                .startDate("2024-08-01")
                .endDate("2025-05-31")
                .currentMatchday(5)
                .build();

        var extCompetition = ExternalCompetition.builder()
                .id(ExternalId.of(2021).get())
                .name("Premier League")
                .code(code)
                .currentSeason(extSeason)
                .build();

        var dbSeason = season(seasonId, 2024);
        dbSeason.setCurrentRoundId(currentRoundId);
        dbSeason.setCurrentMatchDay(2);

        var currentRound = round(currentRoundId, seasonId, 2);
        var nextRound = round(nextRoundId, seasonId, 5);

        var extMatch = externalMatch(100, 1, 57, 61);
        var dbRound = round(UUID.randomUUID(), seasonId, 1);
        var dbHome = team(UUID.randomUUID(), 57, "Arsenal", "ARS");
        var dbAway = team(UUID.randomUUID(), 61, "Chelsea", "CHE");

        when(footballDataGateway.fetchCompetition(code)).thenReturn(right(extCompetition));
        when(seasonRepo.findByClientId(2024)).thenReturn(Optional.of(dbSeason));
        when(footballDataGateway.fetchMatchesForCompetition(code)).thenReturn(right(List.of(extMatch)));
        when(roundRepo.findBySeasonIdAndPosition(seasonId, 1)).thenReturn(Optional.of(dbRound));
        when(roundRepo.findById(currentRoundId)).thenReturn(Optional.of(currentRound));
        when(roundRepo.findBySeasonIdAndPosition(seasonId, 5)).thenReturn(Optional.of(nextRound));
        when(teamRepo.findByClientId(57)).thenReturn(Optional.of(dbHome));
        when(teamRepo.findByClientId(61)).thenReturn(Optional.of(dbAway));
        when(matchRepo.findByClientId(100)).thenReturn(Optional.empty());
        when(matchRepo.create(any(Match.class))).thenAnswer(inv -> inv.getArgument(0));

        useCase.execute(code);

        verify(seasonRepo).save(argThat(s -> nextRoundId.equals(s.getCurrentRoundId()) && s.getCurrentMatchDay() == 5));
    }

    @Test
    @DisplayName("should no-op when external matchday round is missing")
    void shouldNoOpWhenExternalMatchdayRoundMissing() {
        var seasonId = UUID.randomUUID();
        var currentRoundId = UUID.randomUUID();
        var code = comp("PL");

        var extSeason = ExternalSeason.builder()
                .id(ExternalId.of(2024).get())
                .startDate("2024-08-01")
                .endDate("2025-05-31")
                .currentMatchday(5)
                .build();

        var extCompetition = ExternalCompetition.builder()
                .id(ExternalId.of(2021).get())
                .name("Premier League")
                .code(code)
                .currentSeason(extSeason)
                .build();

        var dbSeason = season(seasonId, 2024);
        dbSeason.setCurrentRoundId(currentRoundId);
        dbSeason.setCurrentMatchDay(2);

        var currentRound = round(currentRoundId, seasonId, 2);

        var extMatch = externalMatch(100, 1, 57, 61);
        var dbRound = round(UUID.randomUUID(), seasonId, 1);
        var dbHome = team(UUID.randomUUID(), 57, "Arsenal", "ARS");
        var dbAway = team(UUID.randomUUID(), 61, "Chelsea", "CHE");

        when(footballDataGateway.fetchCompetition(code)).thenReturn(right(extCompetition));
        when(seasonRepo.findByClientId(2024)).thenReturn(Optional.of(dbSeason));
        when(footballDataGateway.fetchMatchesForCompetition(code)).thenReturn(right(List.of(extMatch)));
        when(roundRepo.findBySeasonIdAndPosition(seasonId, 1)).thenReturn(Optional.of(dbRound));
        when(roundRepo.findById(currentRoundId)).thenReturn(Optional.of(currentRound));
        when(roundRepo.findBySeasonIdAndPosition(seasonId, 5)).thenReturn(Optional.empty());
        when(teamRepo.findByClientId(57)).thenReturn(Optional.of(dbHome));
        when(teamRepo.findByClientId(61)).thenReturn(Optional.of(dbAway));
        when(matchRepo.findByClientId(100)).thenReturn(Optional.empty());
        when(matchRepo.create(any(Match.class))).thenAnswer(inv -> inv.getArgument(0));

        useCase.execute(code);

        verify(seasonRepo, never()).save(any());
    }

    @Test
    @DisplayName("should import existing match (update)")
    void shouldUpdateExistingMatch() {
        var code = comp("PL");
        var extCompetition = competition("PL", 2024);
        var extMatch = externalMatch(100, 1, 57, 61);

        UUID seasonId = UUID.randomUUID();
        UUID roundId = UUID.randomUUID();
        UUID homeId = UUID.randomUUID();
        UUID awayId = UUID.randomUUID();

        var dbSeason = season(seasonId, 2024);
        var dbRound = round(roundId, seasonId, 1);
        var dbHome = team(homeId, 57, "Arsenal FC", "ARS");
        var dbAway = team(awayId, 61, "Chelsea FC", "CHE");

        var existing = Match.builder()
                .id(UUID.randomUUID())
                .clientId(100)
                .seasonId(seasonId)
                .roundId(roundId)
                .homeTeamId(homeId)
                .awayTeamId(awayId)
                .slug("ars-che")
                .status(MatchStatus.SCHEDULED)
                .matchday(1)
                .kickOff(OffsetDateTime.now().withNano(0))
                .build();

        when(footballDataGateway.fetchCompetition(code)).thenReturn(right(extCompetition));
        when(seasonRepo.findByClientId(2024)).thenReturn(Optional.of(dbSeason));
        when(footballDataGateway.fetchMatchesForCompetition(code)).thenReturn(right(List.of(extMatch)));
        when(roundRepo.findBySeasonIdAndPosition(seasonId, 1)).thenReturn(Optional.of(dbRound));
        when(teamRepo.findByClientId(57)).thenReturn(Optional.of(dbHome));
        when(teamRepo.findByClientId(61)).thenReturn(Optional.of(dbAway));
        when(matchRepo.findByClientId(100)).thenReturn(Optional.of(existing));
        when(matchRepo.update(any(Match.class))).thenAnswer(inv -> inv.getArgument(0));

        Either<ImportError, ImportSummary> result = useCase.execute(code);

        assertThat(result.isRight()).isTrue();
        var summary = result.get();
        assertThat(summary.getCreated()).isEqualTo(0);
        assertThat(summary.getUpdated()).isEqualTo(1);
        assertThat(summary.getFailed()).isEqualTo(0);
        assertThat(summary.isSuccessful()).isTrue();

        verify(matchRepo, never()).create(any(Match.class));
        verify(matchRepo).update(any(Match.class));
    }

    @Test
    @DisplayName("should handle multiple matches")
    void shouldHandleMultipleMatches() {
        var code = comp("PL");
        var extCompetition = competition("PL", 2024);
        var extMatch1 = externalMatch(100, 1, 57, 61);
        var extMatch2 = externalMatch(101, 1, 61, 57);

        UUID seasonId = UUID.randomUUID();
        UUID roundId = UUID.randomUUID();
        UUID homeId = UUID.randomUUID();
        UUID awayId = UUID.randomUUID();

        var dbSeason = season(seasonId, 2024);
        var dbRound = round(roundId, seasonId, 1);
        var dbHome = team(homeId, 57, "Arsenal FC", "ARS");
        var dbAway = team(awayId, 61, "Chelsea FC", "CHE");

        when(footballDataGateway.fetchCompetition(code)).thenReturn(right(extCompetition));
        when(seasonRepo.findByClientId(2024)).thenReturn(Optional.of(dbSeason));
        when(footballDataGateway.fetchMatchesForCompetition(code)).thenReturn(right(List.of(extMatch1, extMatch2)));
        when(roundRepo.findBySeasonIdAndPosition(seasonId, 1)).thenReturn(Optional.of(dbRound));
        when(teamRepo.findByClientId(any()))
                .thenReturn(Optional.of(dbHome), Optional.of(dbAway), Optional.of(dbAway), Optional.of(dbHome));
        when(matchRepo.findByClientId(any())).thenReturn(Optional.empty());
        when(matchRepo.create(any(Match.class))).thenAnswer(inv -> inv.getArgument(0));

        var result = useCase.execute(code);

        assertThat(result.isRight()).isTrue();
        var summary = result.get();
        assertThat(summary.getTotalMatches()).isEqualTo(2);
        assertThat(summary.getCreated()).isEqualTo(2);
        assertThat(summary.getFailed()).isEqualTo(0);
        assertThat(summary.isSuccessful()).isTrue();

        verify(matchRepo, times(2)).create(any(Match.class));
        verify(matchRepo, never()).update(any(Match.class));
    }

    @Test
    @DisplayName("should return Left when API call fails")
    void shouldFailWhenApiFails() {
        var code = comp("PL");

        when(footballDataGateway.fetchCompetition(code)).thenReturn(Either.left(ApiError.of("API unavailable", 503)));

        var result = useCase.execute(code);

        assertThat(result.isLeft()).isTrue();
        verify(matchRepo, never()).create(any());
        verify(matchRepo, never()).update(any());
    }

    @Test
    @DisplayName("should return Left when season missing")
    void shouldFailWhenSeasonMissing() {
        var code = comp("PL");
        var extCompetition = competition("PL", 2024);

        when(footballDataGateway.fetchCompetition(code)).thenReturn(right(extCompetition));
        when(seasonRepo.findByClientId(2024)).thenReturn(Optional.empty());

        var result = useCase.execute(code);

        assertThat(result.isLeft()).isTrue();
        assertThat(result.getLeft()).isInstanceOf(DatabaseError.class);
    }

    @Test
    @DisplayName("should handle partial failures")
    void shouldHandlePartialFailures() {
        var code = comp("PL");
        var extCompetition = competition("PL", 2024);
        var extMatch1 = externalMatch(100, 1, 57, 61);
        var extMatch2 = externalMatch(101, 1, 57, 61);

        UUID seasonId = UUID.randomUUID();
        UUID roundId = UUID.randomUUID();
        UUID homeId = UUID.randomUUID();
        UUID awayId = UUID.randomUUID();

        var dbSeason = season(seasonId, 2024);
        var dbRound = round(roundId, seasonId, 1);
        var dbHome = team(homeId, 57, "Arsenal FC", "ARS");
        var dbAway = team(awayId, 61, "Chelsea FC", "CHE");

        when(footballDataGateway.fetchCompetition(code)).thenReturn(right(extCompetition));
        when(seasonRepo.findByClientId(2024)).thenReturn(Optional.of(dbSeason));
        when(footballDataGateway.fetchMatchesForCompetition(code)).thenReturn(right(List.of(extMatch1, extMatch2)));
        when(roundRepo.findBySeasonIdAndPosition(seasonId, 1)).thenReturn(Optional.of(dbRound));
        when(teamRepo.findByClientId(57)).thenReturn(Optional.of(dbHome));
        when(teamRepo.findByClientId(61)).thenReturn(Optional.of(dbAway));
        when(matchRepo.findByClientId(any())).thenReturn(Optional.empty());

        when(matchRepo.create(any(Match.class)))
                .thenAnswer(inv -> inv.getArgument(0))
                .thenThrow(new RuntimeException("DB Error"));

        var result = useCase.execute(code);

        assertThat(result.isRight()).isTrue();
        var summary = result.get();

        assertThat(summary.getCreated()).isEqualTo(1);
        assertThat(summary.getFailed()).isEqualTo(1);
        assertThat(summary.isPartialSuccess()).isTrue();
        assertThat(summary.getErrors()).hasSize(1);
    }

    @Test
    @DisplayName("should record failure when team missing")
    void shouldRecordFailureWhenTeamMissing() {
        var code = comp("PL");
        var extCompetition = competition("PL", 2024);
        var extMatch = externalMatch(100, 1, 57, 61);

        UUID seasonId = UUID.randomUUID();
        UUID roundId = UUID.randomUUID();

        var dbSeason = season(seasonId, 2024);
        var dbRound = round(roundId, seasonId, 1);

        when(footballDataGateway.fetchCompetition(code)).thenReturn(right(extCompetition));
        when(seasonRepo.findByClientId(2024)).thenReturn(Optional.of(dbSeason));
        when(footballDataGateway.fetchMatchesForCompetition(code)).thenReturn(right(List.of(extMatch)));
        when(roundRepo.findBySeasonIdAndPosition(seasonId, 1)).thenReturn(Optional.of(dbRound));
        when(teamRepo.findByClientId(any())).thenReturn(Optional.empty());

        var result = useCase.execute(code);

        assertThat(result.isRight()).isTrue();
        var summary = result.get();

        assertThat(summary.getTotalMatches()).isEqualTo(1);
        assertThat(summary.getFailed()).isEqualTo(1);
        assertThat(summary.getErrors()).hasSize(1);
        verify(matchRepo, never()).create(any());
    }

    @Test
    @DisplayName("should record failure when round missing")
    void shouldRecordFailureWhenRoundMissing() {
        var code = comp("PL");
        var extCompetition = competition("PL", 2024);
        var extMatch = externalMatch(100, 1, 57, 61);

        UUID seasonId = UUID.randomUUID();
        var dbSeason = season(seasonId, 2024);

        when(footballDataGateway.fetchCompetition(code)).thenReturn(right(extCompetition));
        when(seasonRepo.findByClientId(2024)).thenReturn(Optional.of(dbSeason));
        when(footballDataGateway.fetchMatchesForCompetition(code)).thenReturn(right(List.of(extMatch)));
        when(roundRepo.findBySeasonIdAndPosition(seasonId, 1)).thenReturn(Optional.empty());

        var result = useCase.execute(code);

        assertThat(result.isRight()).isTrue();
        var summary = result.get();
        assertThat(summary.getFailed()).isEqualTo(1);
        assertThat(summary.getErrors()).hasSize(1);
        verify(matchRepo, never()).create(any());
    }

    @Test
    @DisplayName("should map score when present")
    void shouldMapScoreWhenPresent() {
        var code = comp("PL");
        var extCompetition = competition("PL", 2024);
        var extMatch = externalMatchWithScore(100, 1, 57, 61, 1, 2);

        UUID seasonId = UUID.randomUUID();
        UUID roundId = UUID.randomUUID();
        UUID homeId = UUID.randomUUID();
        UUID awayId = UUID.randomUUID();

        var dbSeason = season(seasonId, 2024);
        var dbRound = round(roundId, seasonId, 1);
        var dbHome = team(homeId, 57, "Arsenal FC", "ARS");
        var dbAway = team(awayId, 61, "Chelsea FC", "CHE");

        when(footballDataGateway.fetchCompetition(code)).thenReturn(right(extCompetition));
        when(seasonRepo.findByClientId(2024)).thenReturn(Optional.of(dbSeason));
        when(footballDataGateway.fetchMatchesForCompetition(code)).thenReturn(right(List.of(extMatch)));
        when(roundRepo.findBySeasonIdAndPosition(seasonId, 1)).thenReturn(Optional.of(dbRound));
        when(teamRepo.findByClientId(57)).thenReturn(Optional.of(dbHome));
        when(teamRepo.findByClientId(61)).thenReturn(Optional.of(dbAway));
        when(matchRepo.findByClientId(100)).thenReturn(Optional.empty());
        when(matchRepo.create(any(Match.class))).thenAnswer(inv -> inv.getArgument(0));

        useCase.execute(code);

        verify(matchRepo).create(argThat(m -> {
            Score score = m.getScore();
            return score != null && score.getHomeGoals() == 1 && score.getAwayGoals() == 2;
        }));
    }

    @Test
    @DisplayName("should reject invalid competition code")
    void shouldRejectInvalidCompetitionCode() {
        Either<ImportError, CompetitionCode> result = CompetitionCode.of("invalid");

        assertThat(result.isLeft()).isTrue();
        assertThat(result.getLeft()).isInstanceOf(ValidationError.class);
    }

    @Test
    @DisplayName("should accept valid competition code")
    void shouldAcceptValidCompetitionCode() {
        Either<ImportError, CompetitionCode> result = CompetitionCode.of("PL");

        assertThat(result.isRight()).isTrue();
        assertThat(result.get().getValue()).isEqualTo("PL");
    }

    @Test
    @DisplayName("should reject invalid external ID")
    void shouldRejectInvalidExternalId() {
        Either<ImportError, ExternalId> result = ExternalId.of(-1);

        assertThat(result.isLeft()).isTrue();
        assertThat(result.getLeft()).isInstanceOf(ValidationError.class);
    }

    @Test
    @DisplayName("should accept valid external ID")
    void shouldAcceptValidExternalId() {
        Either<ImportError, ExternalId> result = ExternalId.of(123);

        assertThat(result.isRight()).isTrue();
        assertThat(result.get().getValue()).isEqualTo(123);
    }
}
