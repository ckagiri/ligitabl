package com.ligitabl.api.web.matches;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

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

import com.ligitabl.api.config.CompetitionDefaults;
import com.ligitabl.api.rest.matchadmin.reschedulematch.RescheduleMatchUseCase;
import com.ligitabl.api.rest.matchadmin.transitionmatchstatus.TransitionMatchStatusUseCase;
import com.ligitabl.api.rest.matchadmin.updatekickoff.UpdateMatchKickoffUseCase;
import com.ligitabl.api.rest.shared.HierarchyValidator;
import com.ligitabl.api.shared.Either;
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
                competitionDefaults);

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
                .thenReturn(Either.right(new HierarchyValidator.HierarchyContext(season, round)));
        Match match = finishedMatch();
        when(matchRepo.findByRoundIdAndSlug(roundId, match.getSlug())).thenReturn(Optional.of(match));

        Model model = new ExtendedModelMap();
        String view = controller.adminModal(match.getSlug(), 5, "Home", "Away", model, new MockHttpServletResponse());

        assertThat(view).isEqualTo("fragments/match-admin-modal :: modal");
        assertThat((List<?>) model.getAttribute("validTransitions")).isEmpty();
        assertThat((Boolean) model.getAttribute("canReschedule")).isFalse();
    }

    @Test
    void finishedMatch_inSetupMode_offersAllTransitionsAndCanReschedule() {
        Season season = Season.builder().id(seasonId).mainContestId(null).build(); // in setup mode
        when(hierarchyValidator.resolveHierarchy("premier-league", 5))
                .thenReturn(Either.right(new HierarchyValidator.HierarchyContext(season, round)));
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
        assertThat(validTransitions)
                .containsExactlyInAnyOrder(
                        MatchStatus.SCHEDULED,
                        MatchStatus.LIVE,
                        MatchStatus.SUSPENDED,
                        MatchStatus.POSTPONED,
                        MatchStatus.CANCELLED);
        assertThat((Boolean) model.getAttribute("canReschedule")).isTrue();

        @SuppressWarnings("unchecked")
        List<MatchAdminController.RoundOption> availableRounds =
                (List<MatchAdminController.RoundOption>) model.getAttribute("availableRounds");
        // finalized round 2 is offered as a target in setup mode (blocked outside setup mode)
        assertThat(availableRounds)
                .extracting(MatchAdminController.RoundOption::position)
                .contains(2);
    }
}
