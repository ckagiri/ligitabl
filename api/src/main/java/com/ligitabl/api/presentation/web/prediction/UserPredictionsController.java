package com.ligitabl.api.presentation.web.prediction;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ligitabl.api.config.CompetitionDefaults;
import com.ligitabl.api.presentation.command.GetUserPredictionCommand;
import com.ligitabl.api.presentation.dto.response.TeamRankDto;
import com.ligitabl.api.presentation.error.UseCaseError;
import com.ligitabl.api.presentation.mapper.ErrorViewMapper;
import com.ligitabl.api.presentation.usecase.GetUserPredictionUseCase;
import com.ligitabl.api.shared.Either;
import com.ligitabl.model.auth.Email;
import com.ligitabl.model.domain.Season;
import com.ligitabl.model.domain.Team;
import com.ligitabl.model.domain.TeamRank;
import com.ligitabl.model.repo.SeasonPredictionRepo;
import com.ligitabl.model.repo.SeasonRepo;
import com.ligitabl.model.repo.TeamRepo;
import com.ligitabl.model.repo.UserRepo;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ui.Model;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.security.Principal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Controller
@RequestMapping("/predictions/user")
@RequiredArgsConstructor
@Slf4j
public class UserPredictionsController {
    private final ObjectMapper objectMapper;
    private final GetUserPredictionUseCase getUserPredictionUseCase;
    private final SeasonRepo seasonRepo;
    private final SeasonPredictionRepo seasonPredictionRepo;
    private final TeamRepo teamRepo;
    private final UserRepo userRepo;
    private final CompetitionDefaults competitionDefaults;
    private final ErrorViewMapper errorMapper;

    /**
    * GET /predictions/user/me - View current user's prediction.
     *
     * <p>Resolves user from Principal:
     * <ul>
     *   <li>Not logged in → Redirects to /prediction/user/guest</li>
     *   <li>Logged in + has prediction → Returns user's prediction (EDITABLE or READONLY_COOLDOWN)</li>
     *   <li>Logged in + no prediction → Returns fallback as CAN_CREATE_ENTRY</li>
     * </ul>
     */
    @GetMapping("/me")
    public String myPredictions(
            @RequestParam(required = false) Integer round,
            Principal principal,
            Model model,
            HttpServletResponse response,
            @RequestHeader(value = "HX-Request", required = false) String hxRequest) {

        log.info("GET /predictions/user/me - round: {}, user: {}",
                round, principal != null ? principal.getName() : "guest");

        // Redirect guests to /guest endpoint - /me implies "my account"
        if (principal == null) {
            String redirect = "redirect:/predictions/user/guest";
            if (round != null) {
                redirect += "?round=" + round;
            }
            return redirect;
        }

        UUID resolvedUserId = resolveAuthenticatedUserId(principal, model, response);
        if (resolvedUserId == null) {
            return "error";
        }

        GetUserPredictionCommand command = buildCommandForMe(resolvedUserId, round);

        Either<UseCaseError, GetUserPredictionUseCase.UserPredictionViewData> result =
                getUserPredictionUseCase.execute(command);

        return result.fold(
                error -> handleError(error, model, response, hxRequest),
                data -> handleSuccess(data, model, hxRequest)
        );
    }

    /**
     * Build command for /me endpoint based on Principal.
     */
    private GetUserPredictionCommand buildCommandForMe(UUID userId, Integer round) {
        Season season = getActiveSeason();
        UUID activeSeasonId = season.getId();

        if (userId == null) {
            return GetUserPredictionCommand.forGuest(activeSeasonId, round);
        }

        boolean hasMainContestEntry = seasonPredictionRepo.existsByUserAndSeason(userId, activeSeasonId);
        return GetUserPredictionCommand.forAuthenticatedUser(
                userId, activeSeasonId, hasMainContestEntry, round
        );
    }

    private UUID resolveAuthenticatedUserId(Principal principal, Model model, HttpServletResponse response) {
        if (principal == null || principal.getName() == null || principal.getName().isBlank()) {
            response.setStatus(401);
            model.addAttribute("error", "Unauthenticated");
            return null;
        }

        try {
            Email email = Email.create(principal.getName());
            return userRepo.findByEmail(email)
                    .map(com.ligitabl.model.domain.User::getId)
                    .orElseGet(() -> {
                        response.setStatus(401);
                        model.addAttribute("error", "User not found");
                        return null;
                    });
        } catch (IllegalArgumentException e) {
            response.setStatus(400);
            model.addAttribute("error", e.getMessage());
            return null;
        }
    }

    /**
     * Handle use case error.
     */
    private String handleError(
            UseCaseError error,
            Model model,
            HttpServletResponse response,
            String hxRequest
    ) {
        response.setStatus(mapErrorToStatus(error));
        model.addAttribute("error", errorMapper.toResponse(error));

        if (hxRequest != null && !hxRequest.isBlank()) {
            return "fragments/error-banner :: banner";
        }
        return "error";
    }

    /**
     * Handle successful use case result.
     */
    private String handleSuccess(
            GetUserPredictionUseCase.UserPredictionViewData data,
            Model model,
            String hxRequest
    ) {
        // Convert rankings to DTOs
        List<TeamRankDto> predictions = enrichRankings(data.rankings());

        // Set model attributes for template
        model.addAttribute("pageTitle", getPageTitle(data));
        model.addAttribute("currentRound", data.currentRound());
        model.addAttribute("viewingRound", data.viewingRound());
        model.addAttribute("isCurrentRound", data.isCurrentRound());
        model.addAttribute("roundState", data.roundState().toLowerCase());
        model.addAttribute("predictions", predictions);

        // Access mode attributes
        model.addAttribute("accessMode", data.accessMode().name());
        model.addAttribute("canSwap", data.canSwap());
        model.addAttribute("canCreateEntry", data.canCreateEntry());
        model.addAttribute("isReadonly", data.isReadonly());
        model.addAttribute("isGuest", data.isGuest());
        model.addAttribute("isViewingOther", data.isViewingOther());
        model.addAttribute("isUserNotFound", data.isUserNotFound());

        // Message for UI banners
        model.addAttribute("message", data.message());
        model.addAttribute("targetDisplayName", data.targetDisplayName());

        // Swap status for cooldown banners
        if (data.swapCooldown() != null) {
            var cooldown = data.swapCooldown();
            var now = Instant.now();
            model.addAttribute("swapStatus", new SwapStatusDTO(
                    cooldown.canSwap(now),
                    cooldown.getStatusMessage(now),
                    cooldown.getLastSwapAtFormatted(),
                    cooldown.initialPredictionMade(),
                    cooldown.swapCount()
            ));
        }

        // Round result for historical views
        if (data.hasRoundResult()) {
            var result = data.roundResult();
            model.addAttribute("roundResult", result);
            model.addAttribute("roundResultRankings", result.getRankings());
            model.addAttribute("totalScore", result.getTotalScore());
            model.addAttribute("totalHits", result.getTotalHits());
            model.addAttribute("zeroesCount", result.getZeroesCount());
        }
        model.addAttribute("hasRoundResult", data.hasRoundResult());

        // Source information
        model.addAttribute("source", data.source().name());
        model.addAttribute("sourceLabel", getSourceLabel(data));

        // Serialize data for JavaScript
        try {
            model.addAttribute("fixturesJson", objectMapper.writeValueAsString(data.matches()));
            model.addAttribute("predictionsJson", objectMapper.writeValueAsString(predictions));
            model.addAttribute("currentStandingsJson", objectMapper.writeValueAsString(data.standingsMap()));
            model.addAttribute("currentPointsJson", objectMapper.writeValueAsString(data.pointsMap()));
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize data", e);
            model.addAttribute("fixturesJson", "{}");
            model.addAttribute("predictionsJson", "[]");
            model.addAttribute("currentStandingsJson", "{}");
            model.addAttribute("currentPointsJson", "{}");
        }

        // Return appropriate view
        if (hxRequest != null && !hxRequest.isBlank()) {
            return "predictions/me :: predictionPage";
        }
        return "predictions/me";
    }

    private Season getActiveSeason() {
        return seasonRepo.findMostRecentSeason(competitionDefaults.defaultCompetitionSlug())
                .orElseThrow(() -> new IllegalStateException("No active season available"));
    }

    private List<TeamRankDto> enrichRankings(List<TeamRank> ranks) {
        if (ranks == null || ranks.isEmpty()) {
            return List.of();
        }

        Map<String, Team> teamsByCode = teamRepo.findAllByCodes(
                        ranks.stream().map(TeamRank::getCode).collect(Collectors.toSet()))
                .stream()
                .collect(Collectors.toMap(Team::getCode, Function.identity()));
        return TeamRankDto.listOf(ranks, teamsByCode);
    }

    /**
     * Get page title based on access mode.
     */
    private String getPageTitle(GetUserPredictionUseCase.UserPredictionViewData data) {
        return switch (data.accessMode()) {
            case EDITABLE, READONLY_COOLDOWN -> "My Predictions";
            case CAN_CREATE_ENTRY -> "Create Prediction";
            case READONLY_GUEST -> "Predictions";
            case READONLY_VIEWING_OTHER -> data.targetDisplayName() != null
                    ? data.targetDisplayName() + "'s Predictions"
                    : "User Predictions";
            case READONLY_USER_NOT_FOUND -> "User Not Found";
        };
    }

    /**
     * Get source label for UI display.
     */
    private String getSourceLabel(GetUserPredictionUseCase.UserPredictionViewData data) {
        return switch (data.source()) {
            case USER_PREDICTION -> "Your Prediction";
            case ROUND_STANDINGS -> "Current Standings";
            case SEASON_BASELINE -> "Season Baseline";
        };
    }

    /**
     * Map use case error to HTTP status code.
     */
    private int mapErrorToStatus(UseCaseError error) {
        return switch (error.type()) {
            case VALIDATION -> 400;
            case NOT_FOUND -> 404;
            case CONFLICT -> 409;
            case BUSINESS_RULE -> 422;
        };
    }

    /**
     * DTO for swap status information displayed in templates.
     */
    public record SwapStatusDTO(
            boolean canSwap,
            String message,
            String lastSwapAt,
            boolean initialPredictionMade,
            int swapCount
    ) {
        /**
         * Check if this is the first swap bonus (can swap without cooldown).
         */
        public boolean isFirstSwapBonus() {
            return initialPredictionMade && swapCount == 0 && canSwap;
        }
    }
}
