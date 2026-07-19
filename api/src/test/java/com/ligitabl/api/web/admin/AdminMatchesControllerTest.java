package com.ligitabl.api.web.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.ui.ExtendedModelMap;
import org.springframework.ui.Model;

import com.ligitabl.api.config.CompetitionDefaults;
import com.ligitabl.api.rest.shared.HierarchyValidator;
import com.ligitabl.api.shared.Either;
import com.ligitabl.api.shared.errors.UseCaseErrors;
import com.ligitabl.model.domain.Competition;
import com.ligitabl.model.domain.Match;
import com.ligitabl.model.domain.MatchStatus;
import com.ligitabl.model.domain.Round;
import com.ligitabl.model.domain.Score;
import com.ligitabl.model.domain.Season;
import com.ligitabl.model.domain.Team;
import com.ligitabl.model.repo.MatchRepo;
import com.ligitabl.model.repo.RoundRepo;
import com.ligitabl.model.repo.TeamRepo;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AdminMatchesControllerTest {

    @Mock
    private HierarchyValidator hierarchyValidator;

    @Mock
    private MatchRepo matchRepo;

    @Mock
    private RoundRepo roundRepo;

    @Mock
    private TeamRepo teamRepo;

    private final CompetitionDefaults competitionDefaults = new CompetitionDefaults("premier-league");

    private AdminMatchesController controller;

    private UUID seasonId;
    private Competition competition;
    private Season season;
    private Round currentRound;

    @BeforeEach
    void setUp() {
        controller =
                new AdminMatchesController(hierarchyValidator, competitionDefaults, matchRepo, roundRepo, teamRepo);

        seasonId = UUID.randomUUID();
        competition = Competition.builder()
                .id(UUID.randomUUID())
                .name("Premier League")
                .build();
        season = Season.builder()
                .id(seasonId)
                .name("2025/26")
                .maxRounds(38)
                .mainContestId(UUID.randomUUID()) // not in setup mode
                .build();
        currentRound = Round.builder()
                .id(UUID.randomUUID())
                .seasonId(seasonId)
                .position(22)
                .name("Round 22")
                .slug("round-22")
                .build();

        when(hierarchyValidator.resolveHierarchy("premier-league"))
                .thenReturn(Either.right(new HierarchyValidator.HierarchyContext(competition, season, currentRound)));
    }

    @Test
    void matchesPage_setsMastheadAttributes() {
        Model model = new ExtendedModelMap();
        String view = controller.matchesPage(model, new MockHttpServletResponse());

        assertThat(view).isEqualTo("admin/matches");
        assertThat(model.getAttribute("competitionName")).isEqualTo("Premier League");
        assertThat(model.getAttribute("seasonName")).isEqualTo("2025/26");
        assertThat(model.getAttribute("currentRound")).isEqualTo(22);
        assertThat(model.getAttribute("maxRounds")).isEqualTo(38);
        assertThat(model.getAttribute("seasonInSetupMode")).isEqualTo(false);
    }

    @Test
    void matchesPage_hierarchyError_rendersErrorView() {
        when(hierarchyValidator.resolveHierarchy("premier-league"))
                .thenReturn(Either.left(UseCaseErrors.notFound("Competition", "premier-league")));

        Model model = new ExtendedModelMap();
        MockHttpServletResponse response = new MockHttpServletResponse();
        String view = controller.matchesPage(model, response);

        assertThat(view).isEqualTo("error");
        assertThat(response.getStatus()).isEqualTo(404);
        assertThat(model.getAttribute("error")).isNotNull();
    }

    @Test
    void matchesData_returnsNormalizedPayload() {
        UUID homeId = UUID.randomUUID();
        UUID awayId = UUID.randomUUID();
        OffsetDateTime kickOff = OffsetDateTime.of(2026, 1, 17, 17, 30, 0, 0, ZoneOffset.ofHours(3));

        Match played = Match.builder()
                .id(UUID.randomUUID())
                .seasonId(seasonId)
                .roundId(currentRound.getId())
                .homeTeamId(homeId)
                .awayTeamId(awayId)
                .slug("home-vs-away")
                .status(MatchStatus.FINISHED)
                .kickOff(kickOff)
                .matchday(22)
                .score(Score.builder().homeGoals(2).awayGoals(1).build())
                .build();
        Match unplayed = Match.builder()
                .id(UUID.randomUUID())
                .seasonId(seasonId)
                .roundId(currentRound.getId())
                .homeTeamId(awayId)
                .awayTeamId(homeId)
                .slug("away-vs-home")
                .status(MatchStatus.SCHEDULED)
                .matchday(23)
                .build();

        when(matchRepo.findBySeasonId(seasonId)).thenReturn(List.of(played, unplayed));
        when(roundRepo.findBySeasonIdOrderByPosition(seasonId)).thenReturn(List.of(currentRound));
        when(teamRepo.findAllByIds(anySet()))
                .thenReturn(List.of(
                        Team.builder()
                                .id(homeId)
                                .name("Arsenal FC")
                                .shorterName("Arsenal")
                                .tla("ARS")
                                .build(),
                        Team.builder()
                                .id(awayId)
                                .name("Chelsea FC")
                                .shorterName("Chelsea")
                                .tla("CHE")
                                .build()));

        ResponseEntity<AdminMatchesData> response = controller.matchesData();

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        AdminMatchesData data = response.getBody();
        assertThat(data).isNotNull();
        assertThat(data.currentRound()).isEqualTo(22);

        // teams are deduped: two matches share the same two teams
        verify(teamRepo).findAllByIds(Set.of(homeId, awayId));
        assertThat(data.teams())
                .extracting(AdminMatchesData.TeamEntry::shorterName)
                .containsExactlyInAnyOrder("Arsenal", "Chelsea");
        assertThat(data.teams()).extracting(AdminMatchesData.TeamEntry::code).containsExactlyInAnyOrder("ARS", "CHE");

        assertThat(data.rounds()).hasSize(1);
        assertThat(data.rounds().getFirst().position()).isEqualTo(22);

        AdminMatchesData.MatchEntry playedEntry = data.matches().getFirst();
        assertThat(playedEntry.score()).isNotNull();
        assertThat(playedEntry.score().homeGoals()).isEqualTo(2);
        assertThat(playedEntry.score().awayGoals()).isEqualTo(1);
        // kickOff is normalized to UTC instant form regardless of source offset
        assertThat(playedEntry.kickOff())
                .isEqualTo(kickOff.toInstant().toString())
                .endsWith("Z");

        AdminMatchesData.MatchEntry unplayedEntry = data.matches().get(1);
        assertThat(unplayedEntry.score()).isNull();
        assertThat(unplayedEntry.kickOff()).isNull();
    }

    @Test
    void matchesData_hierarchyError_returnsMappedStatus() {
        when(hierarchyValidator.resolveHierarchy("premier-league"))
                .thenReturn(Either.left(UseCaseErrors.validation("Competition has no active season")));

        ResponseEntity<AdminMatchesData> response = controller.matchesData();

        assertThat(response.getStatusCode().is4xxClientError()).isTrue();
        assertThat(response.getBody()).isNull();
        verifyNoInteractions(matchRepo, roundRepo, teamRepo);
    }
}
