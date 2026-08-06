package com.ligitabl.api.web.predictions.userpredictions;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.security.Principal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.ui.ExtendedModelMap;
import org.springframework.ui.Model;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ligitabl.api.testsupport.TestClock;
import com.ligitabl.api.auth.CurrentUserPublicId;
import com.ligitabl.api.auth.impersonation.CurrentUserFacade;
import com.ligitabl.api.auth.security.WebUserDetails;
import com.ligitabl.api.config.CompetitionDefaults;
import com.ligitabl.api.rest.standings.FormService;
import com.ligitabl.api.shared.Either;
import com.ligitabl.api.shared.errors.NotFoundError;
import com.ligitabl.api.web.shared.error.ErrorViewMapper;
import com.ligitabl.api.web.shared.fixtures.FixtureJsonMapper;
import com.ligitabl.api.web.shared.season.SeasonPredictionSupport;
import com.ligitabl.api.web.shared.swap.SwapHistoryFormatter;
import com.ligitabl.model.domain.Round;
import com.ligitabl.model.domain.Season;
import com.ligitabl.model.repo.ContestRepo;
import com.ligitabl.model.repo.RoundRepo;
import com.ligitabl.model.repo.SeasonRepo;
import com.ligitabl.model.repo.TeamRepo;
import com.ligitabl.model.repo.UserRepo;

import jakarta.servlet.http.HttpServletResponse;

/**
 * Canonical-URL redirects for {@code ?round=}: a round that resolves to the current one renders
 * exactly what the bare URL renders, so it redirects rather than serving the page under two URLs.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("UserPredictionsController — ?round= canonicalisation")
class UserPredictionsControllerTest {


    private static final int CURRENT_ROUND = 5;

    @Mock
    private CurrentUserPublicId currentUserPublicId;

    @Mock
    private CurrentUserFacade currentUserFacade;

    @Mock
    private GetUserPredictionUseCase getUserPredictionUseCase;

    @Mock
    private SeasonRepo seasonRepo;

    @Mock
    private ContestRepo contestRepo;

    @Mock
    private TeamRepo teamRepo;

    @Mock
    private UserRepo userRepo;

    @Mock
    private ErrorViewMapper errorMapper;

    @Mock
    private FormService formService;

    @Mock
    private FixtureJsonMapper fixtureJsonMapper;

    @Mock
    private RoundRepo roundRepo;

    @Mock
    private WhatIfRecapBuilder whatIfRecapBuilder;

    @Mock
    private SeasonPredictionSupport seasonPredictionSupport;

    @Mock
    private HttpServletResponse response;

    private UserPredictionsController controller;

    private final UUID seasonId = UUID.randomUUID();
    private final UUID currentRoundId = UUID.randomUUID();
    private final UUID userId = UUID.randomUUID();
    private final Principal principal = () -> "user@example.com";
    private final Model model = new ExtendedModelMap();

    @BeforeEach
    void setUp() {
        controller = new UserPredictionsController(
                new ObjectMapper(),
                currentUserPublicId,
                currentUserFacade,
                getUserPredictionUseCase,
                seasonRepo,
                contestRepo,
                teamRepo,
                userRepo,
                new CompetitionDefaults("premier-league"),
                errorMapper,
                formService,
                fixtureJsonMapper,
                roundRepo,
                whatIfRecapBuilder,
                seasonPredictionSupport,
                new SwapHistoryFormatter(),
                TestClock.FIXED);

        Season season = Season.builder()
                .id(seasonId)
                .currentRoundId(currentRoundId)
                .maxRounds(38)
                .build();
        when(seasonRepo.findActiveSeason("premier-league")).thenReturn(Optional.of(season));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private void stubCurrentRound() {
        when(roundRepo.findById(currentRoundId))
                .thenReturn(Optional.of(Round.builder()
                        .id(currentRoundId)
                        .seasonId(seasonId)
                        .position(CURRENT_ROUND)
                        .build()));
    }

    /**
     * The negative cases only care that the request reached the use case rather than short-
     * circuiting into a redirect — a left result is the cheapest way through {@code fold} without
     * building a whole {@link UserPredictionViewData}.
     */
    private void stubUseCaseError() {
        when(getUserPredictionUseCase.execute(any())).thenReturn(Either.left(new NotFoundError("prediction")));
    }

    private void authenticate() {
        WebUserDetails details = new WebUserDetails(userId, "pub-1", "user@example.com", "User", null, List.of());
        Authentication auth = new UsernamePasswordAuthenticationToken(details, null, details.getAuthorities());
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(auth);
        SecurityContextHolder.setContext(context);
    }

    // ─── guest ────────────────────────────────────────────────────────────────

    /**
     * Guests have no round navigation, so <em>every</em> ?round= is a stale or hand-typed URL —
     * past, current, or out of range alike.
     */
    @Test
    @DisplayName("guest ?round=<anything> redirects to the bare /my-table/guest")
    void guest_anyRound_redirects() {
        assertEquals("redirect:/my-table/guest", controller.guestPredictions(CURRENT_ROUND, model, response, null));
        assertEquals("redirect:/my-table/guest", controller.guestPredictions(CURRENT_ROUND - 1, model, response, null));
        assertEquals("redirect:/my-table/guest", controller.guestPredictions(99, model, response, null));
        assertEquals("redirect:/my-table/guest", controller.guestPredictions(0, model, response, null));
        verifyNoInteractions(getUserPredictionUseCase, roundRepo);
    }

    @Test
    @DisplayName("guest with no ?round= renders without consulting the round repo")
    void guest_noRoundParam_doesNotRedirect() {
        stubUseCaseError();
        String view = controller.guestPredictions(null, model, response, null);

        assertNotEquals("redirect:/my-table/guest", view);
        verifyNoInteractions(roundRepo);
        verify(getUserPredictionUseCase).execute(any());
    }

    // ─── authenticated ────────────────────────────────────────────────────────

    @Test
    @DisplayName("/me ?round=<current> redirects to the bare /my-table")
    void me_currentRound_redirects() {
        authenticate();
        stubCurrentRound();

        String view = controller.myPredictions(CURRENT_ROUND, principal, model, response, null);

        assertEquals("redirect:/my-table", view);
        verifyNoInteractions(getUserPredictionUseCase);
    }

    @Test
    @DisplayName("/me ?round=<past> renders instead of redirecting")
    void me_pastRound_doesNotRedirect() {
        stubUseCaseError();
        authenticate();
        stubCurrentRound();

        String view = controller.myPredictions(CURRENT_ROUND - 1, principal, model, response, null);

        assertNotEquals("redirect:/my-table", view);
        verify(getUserPredictionUseCase).execute(any());
    }
}
