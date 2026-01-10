package com.ligitabl.api.controllers.web;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;

import com.ligitabl.api.auth.CurrentUserId;
import com.ligitabl.api.usecases.contest.JoinContestCommand;
import com.ligitabl.api.usecases.contest.JoinContestUseCase;
import com.ligitabl.api.usecases.swap.MakeSwapUseCase;
import com.ligitabl.api.usecases.swap.SwapCommand;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.UUID;

/**
 * Web controller for player-specific pages (requires ROLE_PLAYER).
 * Handles predictions and swaps with server-rendered HTML.
 */
@Controller
@RequiredArgsConstructor
@Slf4j
public class WebPlayerController {

    private final JoinContestUseCase joinContestUseCase;
    private final MakeSwapUseCase makeSwapUseCase;
    private final CurrentUserId currentUserId;

    @GetMapping("/predictions/me")
    public String myPredictions(
            @AuthenticationPrincipal UserDetails userDetails,
            @RequestHeader(value = "HX-Request", required = false) String hxRequest,
            Model model) {

        log.debug("Fetching predictions for user: {}", userDetails.getUsername());

        // TODO: Implement get prediction use case
        // For now, show placeholder message
        model.addAttribute("message", "Predictions page - integration in progress");
        model.addAttribute("userEmail", userDetails.getUsername());

        return "predictions/me";
    }

    @PostMapping("/predictions/swap")
    public String makeSwap(
            @AuthenticationPrincipal UserDetails userDetails,
            @RequestParam String teamA,
            @RequestParam String teamB,
            @RequestHeader(value = "HX-Request", required = false) String hxRequest,
            Model model) {

        UUID userId = currentUserId.require();
        log.debug("Making swap for user: {} - {} <-> {}", userId, teamA, teamB);

        // Create swap command
        var swapCommand = new SwapCommand(teamA, teamB);

        // Execute use case
        var result = makeSwapUseCase.execute(userId, swapCommand);

        return result.fold(
                error -> {
                    log.error("Swap failed for {}: {}", userId, error);
                    model.addAttribute("error", "Swap failed: " + getSwapErrorMessage(error));
                    model.addAttribute("userEmail", userDetails.getUsername());
                    return "predictions/me";
                },
                swapResult -> {
                    log.info("Swap successful for {}: {} <-> {}", userId, teamA, teamB);
                    model.addAttribute("swapResult", swapResult);
                    model.addAttribute("userEmail", userDetails.getUsername());

                    // Return updated prediction fragment for HTMX
                    if (hxRequest != null && !hxRequest.isBlank()) {
                        return "fragments/prediction-table :: predictionTable";
                    }
                    return "redirect:/predictions/me";
                });
    }

    private String getSwapErrorMessage(Object error) {
        // Simple error message extraction
        // In production, use proper pattern matching on SwapError types
        return "Unable to make swap. Please check the round status and cooldown.";
    }

    @PostMapping("/contest/join")
    public String joinContest(
            @AuthenticationPrincipal UserDetails userDetails,
            @RequestParam("rankings") String rankingsJson,
            Model model) {

        UUID userId = currentUserId.require();
        log.debug("User {} joining contest with rankings", userId);

        try {
            // Parse rankings from JSON (simplified - in production use proper JSON parser)
            // Expected format: [{code: "ARS", position: 1}, ...]
            // For now, create a simple parser

            // TODO: Parse rankingsJson properly
            // This is a simplified version - you'll need to parse the actual JSON
            List<JoinContestCommand.TeamRankRequest> rankings = List.of();

            // Create join command
            var joinCommand = new JoinContestCommand(rankings);

            // Execute use case
            var result = joinContestUseCase.execute(userId, joinCommand);

            return result.fold(
                    error -> {
                        log.error("Join contest failed for {}: {}", userId, error);
                        model.addAttribute("error", "Failed to join contest. Please try again.");
                        return "predictions/me";
                    },
                    joinResult -> {
                        log.info("User {} successfully joined contest", userId);
                        return "redirect:/predictions/me";
                    });
        } catch (Exception e) {
            log.error("Error joining contest for {}", userId, e);
            model.addAttribute("error", "An error occurred. Please try again.");
            return "predictions/me";
        }
    }
}
