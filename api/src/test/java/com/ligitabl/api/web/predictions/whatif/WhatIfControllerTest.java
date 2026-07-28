package com.ligitabl.api.web.predictions.whatif;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.security.Principal;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ligitabl.api.auth.security.WebUserDetails;
import com.ligitabl.api.config.CompetitionDefaults;
import com.ligitabl.api.rest.prediction.shared.PredictionAccessMode;
import com.ligitabl.api.rest.prediction.shared.RankingSource;
import com.ligitabl.api.rest.prediction.whatif.ComputeWhatIfUseCase;
import com.ligitabl.api.rest.prediction.whatif.WhatIfError;
import com.ligitabl.api.rest.prediction.whatif.WhatIfResult;
import com.ligitabl.api.shared.Either;
import com.ligitabl.api.web.predictions.userpredictions.GetUserPredictionUseCase;
import com.ligitabl.api.web.predictions.userpredictions.UserPredictionViewData;
import com.ligitabl.model.domain.Season;
import com.ligitabl.model.domain.StandingsMetadata;
import com.ligitabl.model.domain.StandingsTeamRank;
import com.ligitabl.model.domain.TeamRank;
import com.ligitabl.model.repo.ContestRepo;
import com.ligitabl.model.repo.MatchRepo;
import com.ligitabl.model.repo.SeasonRepo;
import com.ligitabl.model.repo.TeamRepo;

import jakarta.servlet.http.HttpServletResponse;
import org.springframework.ui.ExtendedModelMap;
import org.springframework.ui.Model;

@ExtendWith(MockitoExtension.class)
class WhatIfControllerTest {

    private final CompetitionDefaults competitionDefaults = new CompetitionDefaults("premier-league");

    @Mock
    private GetUserPredictionUseCase getUserPredictionUseCase;

    @Mock
    private ComputeWhatIfUseCase computeWhatIfUseCase;

    @Mock
    private SeasonRepo seasonRepo;

    @Mock
    private ContestRepo contestRepo;

    @Mock
    private MatchRepo matchRepo;

    @Mock
    private TeamRepo teamRepo;

    @Mock
    private HttpServletResponse response;

    private WhatIfController controller;

    private final UUID userId = UUID.randomUUID();
    private final Principal principal = () -> "user@example.com";

    @org.junit.jupiter.api.BeforeEach
    void setUp() {
        controller = new WhatIfController(
                getUserPredictionUseCase,
                computeWhatIfUseCase,
                seasonRepo,
                contestRepo,
                matchRepo,
                teamRepo,
                competitionDefaults,
                new ObjectMapper());
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private void authenticate() {
        WebUserDetails details = new WebUserDetails(userId, "pub-1", "user@example.com", "User", null, List.of());
        Authentication auth = new UsernamePasswordAuthenticationToken(details, null, details.getAuthorities());
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(auth);
        SecurityContextHolder.setContext(context);
    }

    // ---- POST /predictions/user/what-if/compute ----

    @Test
    void compute_shouldReturn401_whenUnauthenticated() {
        WhatIfComputeRequest request = new WhatIfComputeRequest(List.of());

        Map<String, Object> body = controller.compute(request, null, response);

        verify(response).setStatus(401);
        assertEquals(false, body.get("success"));
    }

    @Test
    void compute_shouldReturn400_whenMatchIdIsMalformed() {
        authenticate();
        WhatIfComputeRequest request = new WhatIfComputeRequest(List.of(new WhatIfScoreInput("not-a-uuid", 1, 0)));

        Map<String, Object> body = controller.compute(request, principal, response);

        verify(response).setStatus(400);
        assertEquals(false, body.get("success"));
        verifyNoInteractions(computeWhatIfUseCase);
    }

    @Test
    void compute_shouldReturn409_whenRoundNotOpen() {
        authenticate();
        UUID matchId = UUID.randomUUID();
        WhatIfComputeRequest request =
                new WhatIfComputeRequest(List.of(new WhatIfScoreInput(matchId.toString(), 1, 0)));

        when(computeWhatIfUseCase.execute(any())).thenReturn(Either.left(new WhatIfError.RoundNotOpen("LOCKED")));

        Map<String, Object> body = controller.compute(request, principal, response);

        verify(response).setStatus(409);
        assertEquals(false, body.get("success"));
    }

    @Test
    void compute_shouldReturnStandingsMaps_whenSuccessful() {
        authenticate();
        UUID matchId = UUID.randomUUID();
        WhatIfComputeRequest request =
                new WhatIfComputeRequest(List.of(new WhatIfScoreInput(matchId.toString(), 2, 0)));

        StandingsTeamRank arsRank = StandingsTeamRank.builder()
                .ranking(TeamRank.of("ARS", 1))
                .metadata(new StandingsMetadata(2, 2, 0, 0, 6, 5, 1, 4))
                .build();
        when(computeWhatIfUseCase.execute(any())).thenReturn(Either.right(new WhatIfResult(List.of(arsRank))));

        Map<String, Object> body = controller.compute(request, principal, response);

        assertEquals(true, body.get("success"));
        @SuppressWarnings("unchecked")
        Map<String, Integer> standingsMap = (Map<String, Integer>) body.get("standingsMap");
        assertEquals(1, standingsMap.get("ARS"));
        verify(response, never()).setStatus(anyInt());
    }

    // ---- GET /predictions/user/what-if ----

    @Test
    void page_shouldRedirectToMyTable_whenUnauthenticated() {
        Model model = new ExtendedModelMap();

        String view = controller.page(null, model, response, null);

        assertEquals("redirect:/my-table", view);
        verifyNoInteractions(getUserPredictionUseCase);
    }

    @Test
    void page_shouldRedirectToMyTable_whenRoundNotOpen() {
        authenticate();
        Model model = new ExtendedModelMap();
        Season season = Season.builder()
                .id(UUID.randomUUID())
                .currentRoundId(UUID.randomUUID())
                .mainContestId(UUID.randomUUID())
                .build();
        when(seasonRepo.findActiveSeason("premier-league")).thenReturn(Optional.of(season));
        when(contestRepo.existsByUserAndContest(userId, season.getMainContestId()))
                .thenReturn(true);
        when(getUserPredictionUseCase.execute(any()))
                .thenReturn(Either.right(liveViewData(season, "LOCKED", PredictionAccessMode.EDITABLE)));

        String view = controller.page(principal, model, response, null);

        assertEquals("redirect:/my-table", view);
        verifyNoInteractions(matchRepo);
    }

    @Test
    void page_shouldRedirectToMyTable_whenNoLivePrediction() {
        authenticate();
        Model model = new ExtendedModelMap();
        Season season = Season.builder()
                .id(UUID.randomUUID())
                .currentRoundId(UUID.randomUUID())
                .mainContestId(UUID.randomUUID())
                .build();
        when(seasonRepo.findActiveSeason("premier-league")).thenReturn(Optional.of(season));
        when(contestRepo.existsByUserAndContest(userId, season.getMainContestId()))
                .thenReturn(true);
        when(getUserPredictionUseCase.execute(any()))
                .thenReturn(Either.right(liveViewData(season, "OPEN", PredictionAccessMode.CAN_CREATE_ENTRY)));

        String view = controller.page(principal, model, response, null);

        assertEquals("redirect:/my-table", view);
    }

    @Test
    void page_shouldRenderWhatIfPage_whenRoundOpenAndHasLivePrediction() {
        authenticate();
        Model model = new ExtendedModelMap();
        Season season = Season.builder()
                .id(UUID.randomUUID())
                .currentRoundId(UUID.randomUUID())
                .mainContestId(UUID.randomUUID())
                .maxHitPoints(8)
                .build();
        when(seasonRepo.findActiveSeason("premier-league")).thenReturn(Optional.of(season));
        when(contestRepo.existsByUserAndContest(userId, season.getMainContestId()))
                .thenReturn(true);
        when(getUserPredictionUseCase.execute(any()))
                .thenReturn(Either.right(liveViewData(season, "OPEN", PredictionAccessMode.EDITABLE)));
        when(matchRepo.findByRoundIdWithTeams(season.getCurrentRoundId())).thenReturn(List.of());

        String view = controller.page(principal, model, response, null);

        assertEquals("what-if", view);
        assertEquals("What-If", model.asMap().get("pageTitle"));
    }

    private UserPredictionViewData liveViewData(Season season, String roundState, PredictionAccessMode accessMode) {
        return UserPredictionViewData.builder()
                .rankings(List.of(TeamRank.of("ARS", 1)))
                .source(RankingSource.USER_PREDICTION)
                .accessMode(accessMode)
                .matches(Map.of())
                .standingsMap(Map.of())
                .pointsMap(Map.of())
                .goalDifferenceMap(Map.of())
                .currentRound(2)
                .lastRound(38)
                .viewingRound(2)
                .roundState(roundState)
                .isGuest(false)
                .build();
    }
}
