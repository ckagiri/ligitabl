package com.ligitabl.api.controllers.web;

import java.util.List;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;

import com.ligitabl.api.auth.CurrentUserId;
import com.ligitabl.api.usecases.seasonprediction.createseasonpred.CreateSeasonPredictionCommand;
import com.ligitabl.api.usecases.seasonprediction.createseasonpred.CreateSeasonPredictionUseCase;
import com.ligitabl.api.usecases.seasonprediction.makeswap.MakeSwapUseCase;
import com.ligitabl.api.usecases.seasonprediction.makeswap.SwapCommand;
import com.ligitabl.api.usecases.seasonprediction.makeswap.SwapError;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Web controller for player-specific pages (requires ROLE_PLAYER).
 * Handles predictions and swaps with server-rendered HTML.
 */
@Controller
@RequiredArgsConstructor
@Slf4j
public class WebPlayerController {

    private final CreateSeasonPredictionUseCase createSeasonPredUseCase;
    private final MakeSwapUseCase makeSwapUseCase;
    private final CurrentUserId currentUserId;

    @Autowired(required = false)
    private FakeWebDataService fakeDataService;

    @GetMapping("/seasonprediction")
    public String getSeasonPrediction(
            @AuthenticationPrincipal UserDetails userDetails,
            @RequestHeader(value = "HX-Request", required = false) String hxRequest,
            Model model) {

        log.debug("Fetching predictions for user: {}", userDetails.getUsername());

        // Use fake data if available (for UI development without database)
        if (fakeDataService != null) {
            var fakePrediction = fakeDataService.getFakePrediction();
            model.addAttribute("prediction", fakePrediction);
            model.addAttribute("userEmail", userDetails.getUsername());
            model.addAttribute("usingFakeData", true);

            // Add required template variables for current round view
            model.addAttribute("viewingRound", fakePrediction.currentRound());
            model.addAttribute("currentRound", fakePrediction.currentRound());
            model.addAttribute("isCurrentRound", true);
            model.addAttribute("canSwap", fakePrediction.canSwap());
            model.addAttribute("totalPoints", fakePrediction.totalPoints());
            model.addAttribute("teams", fakePrediction.teams());

            // Add Alpine.js data attributes as empty JSON strings (Alpine will use data-* attributes)
            model.addAttribute("predictionsJson", "[]");
            model.addAttribute("isInitialPrediction", false);
            model.addAttribute("currentStandingsJson", "[]");
            model.addAttribute("fixturesJson", "[]");
            model.addAttribute("currentPointsJson", "[]");

            // Add swap status (nullable)
            model.addAttribute("swapStatus", null);

            // Round state
            model.addAttribute("roundState", "open");

            // Page title
            model.addAttribute("pageTitle", "My Prediction");

            return "predictions/me";
        }

        // TODO: Implement get prediction use case
        // For now, show placeholder message
        model.addAttribute("message", "Predictions page - integration in progress");
        model.addAttribute("userEmail", userDetails.getUsername());

        return "predictions/me";
    }

    @PostMapping("/seasonprediction/swap")
    public String makeSwap(
            @AuthenticationPrincipal UserDetails userDetails,
            @RequestParam String teamA,
            @RequestParam String teamB,
            @RequestHeader(value = "HX-Request", required = false) String hxRequest,
            Model model) {

        UUID userId = currentUserId.require();
        log.debug("Making swap for user: {} - {} <-> {}", userId, teamA, teamB);

        var swapCommand = new SwapCommand(teamA, teamB);
        var result = makeSwapUseCase.execute(userId, swapCommand);

        // Use peek/peekLeft for cleaner error handling
        result.peekLeft(error -> {
                    log.error("Swap failed for {}: {}", userId, error);
                    model.addAttribute("error", "Swap failed: " + getSwapErrorMessage(error));
                    model.addAttribute("userEmail", userDetails.getUsername());
                })
                .peek(swapResult -> {
                    log.info("Swap successful for {}: {} <-> {}", userId, teamA, teamB);
                    model.addAttribute("swapResult", swapResult);
                    model.addAttribute("userEmail", userDetails.getUsername());
                });

        // Handle view selection based on result
        if (result.isLeft()) {
            return "predictions/me";
        }

        return isHtmxRequest(hxRequest)
                ? "fragments/prediction-table :: predictionTable"
                : "redirect:/seasonprediction";
    }

    private String getSwapErrorMessage(Object error) {
        if (error instanceof SwapError swapError) {
            return switch (swapError) {
                case SwapError.NoPredictionFound e -> "No prediction found for current season";
                case SwapError.RoundNotOpen e -> "Cannot swap when round is " + e.roundStatus();
                case SwapError.CooldownActive e -> String.format(
                        "Next swap available in %.1f hours", e.hoursRemaining());
                case SwapError.TeamsNotFound e -> "Teams " + e.teamACode() + " and " + e.teamBCode()
                        + " not found in your prediction";
                case SwapError.SeasonCompleted __ -> "Cannot swap in completed season";
                default -> "Unable to make swap. Please try again.";
            };
        }
        return "Unable to make swap. Please try again.";
    }

    private boolean isHtmxRequest(String hxRequest) {
        return hxRequest != null && !hxRequest.isBlank();
    }

    @PostMapping("/seasonprediction")
    public String createSeasonPrediction(
            @AuthenticationPrincipal UserDetails userDetails,
            @RequestParam("rankings") String rankingsJson,
            Model model) {

        UUID userId = currentUserId.require();
        log.debug("User {} joining contest with rankings", userId);

        try {
            // TODO: Parse rankingsJson properly
            // Expected format: [{code: "ARS", position: 1}, ...]
            // For now, create a simple parser - use Jackson ObjectMapper in production
            List<CreateSeasonPredictionCommand.TeamRankRequest> rankings = List.of();

            var joinCommand = new CreateSeasonPredictionCommand(rankings);
            var result = createSeasonPredUseCase.execute(userId, joinCommand);

            // Use peek/peekLeft for side effects
            result.peekLeft(error -> {
                        log.error("Join contest failed for {}: {}", userId, error);
                        model.addAttribute("error", "Failed to join contest. Please try again.");
                    })
                    .peek(joinResult -> log.info("User {} successfully joined contest", userId));

            // Return appropriate view based on result
            return result.isLeft() ? "predictions/me" : "redirect:/seasonprediction";

        } catch (Exception e) {
            log.error("Error joining contest for {}", userId, e);
            model.addAttribute("error", "An error occurred. Please try again.");
            return "predictions/me";
        }
    }
}
