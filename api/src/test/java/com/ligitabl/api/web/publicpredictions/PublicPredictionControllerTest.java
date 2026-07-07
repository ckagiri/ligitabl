package com.ligitabl.api.web.publicpredictions;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.ui.ExtendedModelMap;
import org.springframework.ui.Model;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ligitabl.api.config.CompetitionDefaults;
import com.ligitabl.api.rest.standings.FormService;
import com.ligitabl.api.shared.Either;
import com.ligitabl.api.web.shared.fixtures.FixtureJsonMapper;
import com.ligitabl.model.domain.Competition;
import com.ligitabl.model.domain.Round;
import com.ligitabl.model.domain.Season;
import com.ligitabl.model.domain.SeasonSlug;
import com.ligitabl.model.repo.CompetitionRepo;
import com.ligitabl.model.repo.RoundRepo;
import com.ligitabl.model.repo.SeasonRepo;

@ExtendWith(MockitoExtension.class)
class PublicPredictionControllerTest {

    @Mock
    private CompetitionRepo competitionRepo;

    @Mock
    private SeasonRepo seasonRepo;

    @Mock
    private RoundRepo roundRepo;

    @Mock
    private GetPublicPredictionUseCase getPublicPredictionUseCase;

    @Mock
    private FormService formService;

    private final CompetitionDefaults competitionDefaults = new CompetitionDefaults("premier-league");
    private final FixtureJsonMapper fixtureJsonMapper = new FixtureJsonMapper();
    private final ObjectMapper objectMapper = new ObjectMapper();

    private PublicPredictionController controller;

    private UUID competitionId;
    private UUID seasonId;
    private UUID roundId;
    private Competition competition;
    private Season season;

    @BeforeEach
    void setUp() {
        controller = new PublicPredictionController(
                competitionRepo,
                seasonRepo,
                roundRepo,
                competitionDefaults,
                getPublicPredictionUseCase,
                formService,
                fixtureJsonMapper,
                objectMapper);

        competitionId = UUID.randomUUID();
        seasonId = UUID.randomUUID();
        roundId = UUID.randomUUID();
        competition = Competition.builder()
                .id(competitionId)
                .name("Premier League")
                .code("PL")
                .build();
        season = Season.builder()
                .id(seasonId)
                .competitionId(competitionId)
                .currentRoundId(roundId)
                .slug(SeasonSlug.of("2025-26"))
                .build();

        // Not needed by the invalid-season-shorthand test, which bails out before resolving the
        // competition at all.
        lenient().when(competitionRepo.findBySlug("premier-league")).thenReturn(Optional.of(competition));
        // Only reached for the current-round comparison-options branch (hasRoundResult == false).
        lenient().when(formService.buildFormMap(any(), anyInt())).thenReturn(Map.of());
    }

    @Test
    void redirectToCurrentRound_redirectsToCanonicalUrl() {
        when(seasonRepo.findActiveSeason(competitionId)).thenReturn(Optional.of(season));
        when(roundRepo.findById(roundId))
                .thenReturn(Optional.of(Round.builder().id(roundId).position(13).build()));

        String view =
                controller.redirectToCurrentRound("T2ADsSc8hQ", new ExtendedModelMap(), new MockHttpServletResponse());

        assertThat(view).isEqualTo("redirect:/u/T2ADsSc8hQ/2526/gw/13");
    }

    @Test
    void redirectToCurrentRound_noActiveSeason_rendersError() {
        when(seasonRepo.findActiveSeason(competitionId)).thenReturn(Optional.empty());
        Model model = new ExtendedModelMap();
        MockHttpServletResponse response = new MockHttpServletResponse();

        String view = controller.redirectToCurrentRound("T2ADsSc8hQ", model, response);

        assertThat(view).isEqualTo("error");
        assertThat(response.getStatus()).isEqualTo(404);
    }

    @Test
    void publicPrediction_requestedRoundMatchesViewingRound_rendersPage() {
        when(seasonRepo.findByCompetitionIdAndSlug(competitionId, SeasonSlug.of("2025-26")))
                .thenReturn(Optional.of(season));
        var data = PublicPredictionViewData.builder()
                .rows(List.of())
                .userFound(true)
                .hasPrediction(true)
                .targetDisplayName("Jane Doe")
                .currentRound(13)
                .lastRound(38)
                .viewingRound(13)
                .minRound(1)
                .seasonCompleted(false)
                .hasRoundResult(false)
                .build();
        when(getPublicPredictionUseCase.execute(any())).thenReturn(Either.right(data));

        String view = controller.publicPrediction(
                "T2ADsSc8hQ", "2526", 13, new ExtendedModelMap(), new MockHttpServletResponse(), null);

        assertThat(view).isEqualTo("public-predictions");
    }

    @Test
    void publicPrediction_currentRound_populatesComparisonOptionsModel() {
        when(seasonRepo.findByCompetitionIdAndSlug(competitionId, SeasonSlug.of("2025-26")))
                .thenReturn(Optional.of(season));
        var data = PublicPredictionViewData.builder()
                .rows(List.of())
                .userFound(true)
                .hasPrediction(true)
                .currentRound(13)
                .lastRound(38)
                .viewingRound(13)
                .minRound(1)
                .seasonCompleted(false)
                .hasRoundResult(false)
                .build();
        when(getPublicPredictionUseCase.execute(any())).thenReturn(Either.right(data));
        ExtendedModelMap model = new ExtendedModelMap();

        controller.publicPrediction("T2ADsSc8hQ", "2526", 13, model, new MockHttpServletResponse(), null);

        assertThat(model.getAttribute("predictionsJson")).isEqualTo("[]");
        assertThat(model.getAttribute("fixturesJson")).isEqualTo("{}");
        assertThat(model.getAttribute("currentStandingsJson")).isEqualTo("{}");
        assertThat(model.getAttribute("currentPointsJson")).isEqualTo("{}");
        assertThat(model.getAttribute("currentGoalDifferenceJson")).isEqualTo("{}");
        assertThat(model.getAttribute("formJson")).isEqualTo("{}");
        assertThat(model.getAttribute("hasFormData")).isEqualTo(false);
    }

    @Test
    void publicPrediction_historicalRound_skipsComparisonOptionsModel() {
        when(seasonRepo.findByCompetitionIdAndSlug(competitionId, SeasonSlug.of("2025-26")))
                .thenReturn(Optional.of(season));
        var data = PublicPredictionViewData.builder()
                .rows(List.of())
                .userFound(true)
                .hasPrediction(true)
                .currentRound(13)
                .lastRound(38)
                .viewingRound(8)
                .minRound(1)
                .seasonCompleted(false)
                .hasRoundResult(true)
                .totalScore(150)
                .totalHits(50)
                .zeroesCount(3)
                .build();
        when(getPublicPredictionUseCase.execute(any())).thenReturn(Either.right(data));
        ExtendedModelMap model = new ExtendedModelMap();

        controller.publicPrediction("T2ADsSc8hQ", "2526", 8, model, new MockHttpServletResponse(), null);

        assertThat(model.getAttribute("predictionsJson")).isNull();
        assertThat(model.getAttribute("hasFormData")).isNull();
        verifyNoInteractions(formService);
    }

    @Test
    void publicPrediction_useCaseClampsRound_redirectsToCanonicalUrl() {
        when(seasonRepo.findByCompetitionIdAndSlug(competitionId, SeasonSlug.of("2025-26")))
                .thenReturn(Optional.of(season));
        var data = PublicPredictionViewData.builder()
                .rows(List.of())
                .userFound(true)
                .hasPrediction(true)
                .currentRound(13)
                .lastRound(38)
                .viewingRound(13) // clamped by the use case; requested was 99
                .minRound(1)
                .seasonCompleted(false)
                .hasRoundResult(false)
                .build();
        when(getPublicPredictionUseCase.execute(any())).thenReturn(Either.right(data));

        String view = controller.publicPrediction(
                "T2ADsSc8hQ", "2526", 99, new ExtendedModelMap(), new MockHttpServletResponse(), null);

        assertThat(view).isEqualTo("redirect:/u/T2ADsSc8hQ/2526/gw/13");
    }

    @Test
    void publicPrediction_invalidSeasonShorthand_rendersError() {
        MockHttpServletResponse response = new MockHttpServletResponse();

        String view =
                controller.publicPrediction("T2ADsSc8hQ", "not-a-season", 13, new ExtendedModelMap(), response, null);

        assertThat(view).isEqualTo("error");
        assertThat(response.getStatus()).isEqualTo(404);
        verifyNoInteractions(seasonRepo, getPublicPredictionUseCase);
    }

    @Test
    void publicPrediction_seasonNotFound_rendersError() {
        when(seasonRepo.findByCompetitionIdAndSlug(competitionId, SeasonSlug.of("2025-26")))
                .thenReturn(Optional.empty());
        MockHttpServletResponse response = new MockHttpServletResponse();

        String view = controller.publicPrediction("T2ADsSc8hQ", "2526", 13, new ExtendedModelMap(), response, null);

        assertThat(view).isEqualTo("error");
        assertThat(response.getStatus()).isEqualTo(404);
    }

    @Test
    void publicPrediction_useCaseError_rendersError() {
        when(seasonRepo.findByCompetitionIdAndSlug(competitionId, SeasonSlug.of("2025-26")))
                .thenReturn(Optional.of(season));
        when(getPublicPredictionUseCase.execute(any()))
                .thenReturn(Either.left(new GetPublicPredictionUseCase.Error.CurrentRoundNotFound(seasonId)));
        MockHttpServletResponse response = new MockHttpServletResponse();

        String view = controller.publicPrediction("T2ADsSc8hQ", "2526", 13, new ExtendedModelMap(), response, null);

        assertThat(view).isEqualTo("error");
        assertThat(response.getStatus()).isEqualTo(404);
    }

    @Test
    void publicPrediction_hxRequest_returnsFragmentView() {
        when(seasonRepo.findByCompetitionIdAndSlug(competitionId, SeasonSlug.of("2025-26")))
                .thenReturn(Optional.of(season));
        var data = PublicPredictionViewData.builder()
                .rows(List.of())
                .userFound(false)
                .hasPrediction(false)
                .currentRound(13)
                .lastRound(38)
                .viewingRound(13)
                .minRound(13)
                .seasonCompleted(false)
                .hasRoundResult(false)
                .build();
        when(getPublicPredictionUseCase.execute(any())).thenReturn(Either.right(data));

        String view = controller.publicPrediction(
                "T2ADsSc8hQ", "2526", 13, new ExtendedModelMap(), new MockHttpServletResponse(), "true");

        assertThat(view).isEqualTo("public-predictions :: publicPredictionPage");
    }
}
