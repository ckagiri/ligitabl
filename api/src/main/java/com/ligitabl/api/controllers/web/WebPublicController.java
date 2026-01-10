package com.ligitabl.api.controllers.web;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;

import com.ligitabl.api.usecases.leaderboard.GetLeaderboardQuery;
import com.ligitabl.api.usecases.leaderboard.GetLeaderboardUseCase;
import com.ligitabl.api.usecases.match.getdefaultroundmatches.GetDefaultRoundMatchesUseCase;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Web controller for public pages (no authentication required).
 * Serves server-rendered HTML using Thymeleaf templates.
 */
@Controller
@RequiredArgsConstructor
@Slf4j
public class WebPublicController {

    private final GetLeaderboardUseCase getLeaderboardUseCase;
    private final GetDefaultRoundMatchesUseCase getDefaultRoundMatchesUseCase;

    @GetMapping("/")
    public String home(
            @RequestHeader(value = "HX-Request", required = false) String hxRequest, Model model) {
        // Landing page - just render the template
        log.debug("Rendering home page");
        return "index";
    }

    @GetMapping("/leaderboard")
    public String leaderboard(
            @RequestParam(required = false, defaultValue = "FS") String phase,
            @RequestHeader(value = "HX-Request", required = false) String hxRequest,
            Model model) {

        log.debug("Fetching leaderboard for phase: {}", phase);

        // Call the use case
        var query = new GetLeaderboardQuery(phase);
        var result = getLeaderboardUseCase.execute(query);

        return result.fold(
                error -> {
                    log.error("Error fetching leaderboard: {}", error);
                    model.addAttribute("error", "Unable to load leaderboard");
                    return "leaderboard";
                },
                leaderboardResult -> {
                    model.addAttribute("leaderboard", leaderboardResult);
                    model.addAttribute("currentPhase", phase);
                    model.addAttribute("phases", new String[] {"FS", "Q1", "Q2", "Q3", "Q4", "H1", "H2"});

                    // HTMX partial update vs full page
                    if (hxRequest != null && !hxRequest.isBlank()) {
                        return "leaderboard :: leaderboardContent";
                    }
                    return "leaderboard";
                });
    }

    @GetMapping("/standings")
    public String standings(
            @RequestHeader(value = "HX-Request", required = false) String hxRequest, Model model) {

        log.debug("Fetching default standings");

        // TODO: Implement standings use case
        // For now, return empty page with message
        model.addAttribute("message", "Standings will be available soon");

        return "standings";
    }

    @GetMapping("/matches")
    public String matches(
            @RequestHeader(value = "HX-Request", required = false) String hxRequest, Model model) {

        log.debug("Fetching default round matches");

        // Call the use case
        var result = getDefaultRoundMatchesUseCase.execute();

        return result.fold(
                error -> {
                    log.error("Error fetching matches: {}", error);
                    model.addAttribute("error", "Unable to load matches");
                    return "matches";
                },
                matchesResult -> {
                    model.addAttribute("matches", matchesResult);

                    // HTMX partial update vs full page
                    if (hxRequest != null && !hxRequest.isBlank()) {
                        return "matches :: matchesList";
                    }
                    return "matches";
                });
    }
}
