package com.ligitabl.api.web.matches;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.ui.ExtendedModelMap;
import org.springframework.ui.Model;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ligitabl.api.config.CompetitionDefaults;
import com.ligitabl.api.rest.matchadmin.reschedulematch.RescheduleMatchUseCase;
import com.ligitabl.api.rest.matchadmin.transitionmatchstatus.TransitionMatchStatusUseCase;
import com.ligitabl.api.rest.matchadmin.transitionmatchstatus.TransitionResult;
import com.ligitabl.api.rest.matchadmin.updatekickoff.UpdateMatchKickoffUseCase;
import com.ligitabl.api.rest.shared.HierarchyValidator;
import com.ligitabl.api.shared.Either;
import com.ligitabl.api.shared.errors.UseCaseErrors;
import com.ligitabl.model.domain.Competition;
import com.ligitabl.model.domain.Match;
import com.ligitabl.model.domain.MatchStatus;
import com.ligitabl.model.domain.Round;
import com.ligitabl.model.domain.Season;
import com.ligitabl.model.repo.MatchRepo;
import com.ligitabl.model.repo.RoundRepo;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class MatchAdminControllerTest {

    @Mock
    private HierarchyValidator hierarchyValidator;

    @Mock
    private MatchRepo matchRepo;

    @Mock
    private RoundRepo roundRepo;

    @Mock
    private TransitionMatchStatusUseCase transitionUseCase;

    @Mock
    private RescheduleMatchUseCase rescheduleUseCase;

    @Mock
    private UpdateMatchKickoffUseCase updateKickoffUseCase;

    private final CompetitionDefaults competitionDefaults = new CompetitionDefaults("premier-league");
    private final ObjectMapper objectMapper = new ObjectMapper();

    private MatchAdminController controller;

    private UUID seasonId;
    private UUID roundId;
    private Round round;

    @BeforeEach
    void setUp() {
        controller = new MatchAdminController(
                hierarchyValidator,
                matchRepo,
                roundRepo,
                transitionUseCase,
                rescheduleUseCase,
                updateKickoffUseCase,
                competitionDefaults,
                objectMapper);

        seasonId = UUID.randomUUID();
        roundId = UUID.randomUUID();
        round = Round.builder()
                .id(roundId)
                .seasonId(seasonId)
                .position(5)
                .finalized(false)
                .name("Round 5")
                .slug("round-5")
                .build();
    }

    private Match finishedMatch() {
        return Match.builder()
                .id(UUID.randomUUID())
                .clientId(1)
                .roundId(roundId)
                .homeTeamId(UUID.randomUUID())
                .awayTeamId(UUID.randomUUID())
                .slug("home-vs-away")
                .status(MatchStatus.FINISHED)
                .build();
    }

    @Test
    void finishedMatch_outsideSetupMode_offersNoTransitionsAndCannotReschedule() {
        Season season =
                Season.builder().id(seasonId).mainContestId(UUID.randomUUID()).build();
        when(hierarchyValidator.resolveHierarchy("premier-league", 5))
                .thenReturn(Either.right(new HierarchyValidator.HierarchyContext(mock(Competition.class), season, round)));
        Match match = finishedMatch();
        when(matchRepo.findByRoundIdAndSlug(roundId, match.getSlug())).thenReturn(Optional.of(match));

        Model model = new ExtendedModelMap();
        String view = controller.adminModal(match.getSlug(), 5, "Home", "Away", model, new MockHttpServletResponse());

        assertThat(view).isEqualTo("fragments/match-admin-modal :: modal");
        assertThat((List<?>) model.getAttribute("validTransitions")).isEmpty();
        assertThat((Boolean) model.getAttribute("canReschedule")).isFalse();
    }

    @Test
    void finishedMatch_inSetupMode_offersWhitelistedTransitionsAndCanReschedule() {
        Season season = Season.builder().id(seasonId).mainContestId(null).build(); // in setup mode
        when(hierarchyValidator.resolveHierarchy("premier-league", 5))
                .thenReturn(Either.right(new HierarchyValidator.HierarchyContext(mock(Competition.class), season, round)));
        when(hierarchyValidator.validateCurrentRound(season)).thenReturn(Either.right(round));
        Match match = finishedMatch();
        when(matchRepo.findByRoundIdAndSlug(roundId, match.getSlug())).thenReturn(Optional.of(match));

        Round finalizedRound = Round.builder()
                .id(UUID.randomUUID())
                .seasonId(seasonId)
                .position(2)
                .finalized(true)
                .name("Round 2")
                .slug("round-2")
                .build();
        when(roundRepo.findBySeasonIdOrderByPosition(seasonId)).thenReturn(List.of(round, finalizedRound));

        Model model = new ExtendedModelMap();
        String view = controller.adminModal(match.getSlug(), 5, "Home", "Away", model, new MockHttpServletResponse());

        assertThat(view).isEqualTo("fragments/match-admin-modal :: modal");
        @SuppressWarnings("unchecked")
        List<MatchStatus> validTransitions = (List<MatchStatus>) model.getAttribute("validTransitions");
        assertThat(validTransitions).containsExactlyInAnyOrder(MatchStatus.SCHEDULED, MatchStatus.POSTPONED);
        assertThat((Boolean) model.getAttribute("canReschedule")).isTrue();

        @SuppressWarnings("unchecked")
        List<MatchAdminController.RoundOption> availableRounds =
                (List<MatchAdminController.RoundOption>) model.getAttribute("availableRounds");
        // finalized round 2 is offered as a target in setup mode (blocked outside setup mode)
        assertThat(availableRounds)
                .extracting(MatchAdminController.RoundOption::position)
                .contains(2);
    }

    @Test
    void transitionSuccess_carriesUpdatedMatchAsJsonInHxTrigger() throws Exception {
        UUID matchId = UUID.randomUUID();
        Match updated = Match.builder()
                .id(matchId)
                .clientId(1)
                .roundId(roundId)
                .homeTeamId(UUID.randomUUID())
                .awayTeamId(UUID.randomUUID())
                .slug("home-vs-away")
                .status(MatchStatus.LIVE)
                .matchday(5)
                .build();
        when(transitionUseCase.execute(any()))
                .thenReturn(Either.right(TransitionResult.builder()
                        .matchId(matchId)
                        .matchSlug("home-vs-away")
                        .oldStatus(MatchStatus.SCHEDULED)
                        .newStatus(MatchStatus.LIVE)
                        .roundPosition(5)
                        .build()));
        when(matchRepo.findById(matchId)).thenReturn(Optional.of(updated));

        MockHttpServletResponse response = new MockHttpServletResponse();
        String view = controller.transition("home-vs-away", 5, "LIVE", null, null, new ExtendedModelMap(), response);

        assertThat(view).isEqualTo("fragments/match-admin-modal :: done");
        JsonNode trigger = objectMapper.readTree(response.getHeader("HX-Trigger"));
        JsonNode match = trigger.path("matchUpdated").path("match");
        assertThat(match.path("id").asText()).isEqualTo(matchId.toString());
        assertThat(match.path("status").asText()).isEqualTo("LIVE");
        assertThat(match.path("roundId").asText()).isEqualTo(roundId.toString());
        assertThat(match.path("score").isNull()).isTrue();
    }

    @Test
    void transitionSuccess_fallsBackToBareEventWhenMatchNotFound() {
        UUID matchId = UUID.randomUUID();
        when(transitionUseCase.execute(any()))
                .thenReturn(Either.right(TransitionResult.builder()
                        .matchId(matchId)
                        .matchSlug("home-vs-away")
                        .oldStatus(MatchStatus.SCHEDULED)
                        .newStatus(MatchStatus.LIVE)
                        .roundPosition(5)
                        .build()));
        when(matchRepo.findById(matchId)).thenReturn(Optional.empty());

        MockHttpServletResponse response = new MockHttpServletResponse();
        controller.transition("home-vs-away", 5, "LIVE", null, null, new ExtendedModelMap(), response);

        assertThat(response.getHeader("HX-Trigger")).isEqualTo("matchUpdated");
    }

    @Test
    void kickoffUpdateSuccess_carriesUpdatedMatchAsJsonInHxTrigger() throws Exception {
        UUID matchId = UUID.randomUUID();
        OffsetDateTime newKickOff = OffsetDateTime.of(2026, 8, 15, 14, 30, 0, 0, ZoneOffset.UTC);
        Match updated = Match.builder()
                .id(matchId)
                .clientId(1)
                .roundId(roundId)
                .homeTeamId(UUID.randomUUID())
                .awayTeamId(UUID.randomUUID())
                .slug("home-vs-away")
                .status(MatchStatus.SCHEDULED)
                .kickOff(newKickOff)
                .matchday(5)
                .build();
        when(updateKickoffUseCase.execute(any()))
                .thenReturn(Either.right(new UpdateMatchKickoffUseCase.Result(matchId, "home-vs-away", newKickOff)));
        when(matchRepo.findById(matchId)).thenReturn(Optional.of(updated));

        MockHttpServletResponse response = new MockHttpServletResponse();
        String view =
                controller.updateKickoff("home-vs-away", 5, "2026-08-15", "14:30", 0, new ExtendedModelMap(), response);

        assertThat(view).isEqualTo("fragments/match-admin-modal :: done");
        JsonNode match = objectMapper
                .readTree(response.getHeader("HX-Trigger"))
                .path("matchUpdated")
                .path("match");
        assertThat(match.path("id").asText()).isEqualTo(matchId.toString());
        assertThat(match.path("kickOff").asText())
                .isEqualTo(newKickOff.toInstant().toString());
    }

    @Test
    void transitionFailure_setsNoTriggerHeader() {
        when(transitionUseCase.execute(any())).thenReturn(Either.left(UseCaseErrors.validation("nope")));

        MockHttpServletResponse response = new MockHttpServletResponse();
        String view = controller.transition("home-vs-away", 5, "LIVE", null, null, new ExtendedModelMap(), response);

        assertThat(view).isEqualTo("fragments/match-admin-modal :: error-message");
        assertThat(response.getStatus()).isEqualTo(422);
        assertThat(response.getHeader("HX-Trigger")).isNull();
    }
}
