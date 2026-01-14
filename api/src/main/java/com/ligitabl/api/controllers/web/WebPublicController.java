package com.ligitabl.api.controllers.web;

import com.ligitabl.api.usecases.match.getdefaultroundmatches.GetDefaultRoundMatchesQuery;
import org.springframework.beans.factory.annotation.Autowired;
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

    @Autowired(required = false)
    private FakeWebDataService fakeDataService;

    @GetMapping("/")
    public String home(
            @RequestHeader(value = "HX-Request", required = false) String hxRequest, Model model) {
        // Landing page - just render the template
        log.debug("Rendering home page");
        model.addAttribute("pageTitle", "Home");
        return "index";
    }

    @GetMapping("/leaderboard")
    public String leaderboard(
            @RequestParam(required = false, defaultValue = "FS") String phase,
            @RequestHeader(value = "HX-Request", required = false) String hxRequest,
            Model model) {

        log.debug("Fetching leaderboard for phase: {}", phase);

        // Use fake data if available (for UI development without database)
        if (fakeDataService != null) {
            var fakeLeaderboard = fakeDataService.getFakeLeaderboard(phase);
            model.addAttribute("leaderboard", fakeLeaderboard);
            model.addAttribute("currentPhase", phase);
            model.addAttribute("phases", new String[] {"FS", "Q1", "Q2", "Q3", "Q4", "H1", "H2"});
            model.addAttribute("usingFakeData", true);
            model.addAttribute("pageTitle", "Leaderboard");

            return isHtmxRequest(hxRequest) ? "leaderboard :: leaderboardContent" : "leaderboard";
        }

        var query = new GetLeaderboardQuery(phase);
        getLeaderboardUseCase.execute(query)
            .peekLeft(error -> {
                log.error("Error fetching leaderboard: {}", error);
                model.addAttribute("error", "Unable to load leaderboard");
            })
            .peek(leaderboardResult -> {
                model.addAttribute("leaderboard", leaderboardResult);
                model.addAttribute("currentPhase", phase);
                model.addAttribute("phases", new String[] {"FS", "Q1", "Q2", "Q3", "Q4", "H1", "H2"});
            });

        model.addAttribute("pageTitle", "Leaderboard");
        return isHtmxRequest(hxRequest) ? "leaderboard :: leaderboardContent" : "leaderboard";
    }

    @GetMapping("/standings")
    public String standings(
            @RequestHeader(value = "HX-Request", required = false) String hxRequest, Model model) {

        log.debug("Fetching default standings");

        // TODO: Implement standings use case
        // For now, return empty page with message
        model.addAttribute("message", "Standings will be available soon");
        model.addAttribute("pageTitle", "Standings");

        return "standings";
    }

    @GetMapping("/matches")
    public String matches(
            @RequestHeader(value = "HX-Request", required = false) String hxRequest, Model model) {

        log.debug("Fetching default round matches");

        // Use fake data if available (for UI development without database)
        if (fakeDataService != null) {
            var fakeMatches = fakeDataService.getFakeMatches();
            model.addAttribute("matches", fakeMatches);
            model.addAttribute("usingFakeData", true);
            model.addAttribute("pageTitle", "Matches");

            return isHtmxRequest(hxRequest) ? "matches :: matchesList" : "matches";
        }

        getDefaultRoundMatchesUseCase.execute(GetDefaultRoundMatchesQuery.currentRound(null))
            .peekLeft(error -> {
                log.error("Error fetching matches: {}", error);
                model.addAttribute("error", "Unable to load matches");
            })
            .peek(matchesResult -> model.addAttribute("matches", matchesResult));

        model.addAttribute("pageTitle", "Matches");
        return isHtmxRequest(hxRequest) ? "matches :: matchesList" : "matches";
    }

    private boolean isHtmxRequest(String hxRequest) {
        return hxRequest != null && !hxRequest.isBlank();
    }
}
