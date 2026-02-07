package com.ligitabl.api.web.standings;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;

import com.ligitabl.api.rest.standings.GetDefaultRoundStandingsQuery;
import com.ligitabl.api.rest.standings.GetDefaultRoundStandingsUseCase;
import com.ligitabl.api.shared.errors.UseCaseError;
import com.ligitabl.api.web.shared.error.ErrorMapper;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Controller
@RequestMapping("/rounds")
@RequiredArgsConstructor
@Slf4j
public class RoundStandingsController {

    private final GetDefaultRoundStandingsUseCase getDefaultRoundStandingsUseCase;

    /**
     * GET /rounds/current/standings - View current round standings
     */
    @GetMapping("/current/standings")
    public String getCurrentRoundStandings(Model model, HttpServletRequest request, HttpServletResponse response) {
        log.info("GET /rounds/current/standings");
        return executeUseCase(GetDefaultRoundStandingsQuery.currentRound(null), model, request, response);
    }

    /**
     * GET /rounds/{roundPosition}/standings - View specific round standings
     */
    @GetMapping("/{roundPosition}/standings")
    public String getRoundStandingsByPosition(
            @PathVariable Integer roundPosition,
            Model model,
            HttpServletRequest request,
            HttpServletResponse response) {
        log.info("GET /rounds/{}/standings", roundPosition);
        return executeUseCase(GetDefaultRoundStandingsQuery.byPosition(roundPosition, null), model, request, response);
    }

    private String executeUseCase(
            GetDefaultRoundStandingsQuery query,
            Model model,
            HttpServletRequest request,
            HttpServletResponse response) {
        var result = getDefaultRoundStandingsUseCase.execute(query);

        // Check if this is an HTMX request
        boolean isHtmxRequest = "true".equals(request.getHeader("HX-Request"));

        return result.fold(error -> handleStandingsError(error, model, response), payload -> {
            model.addAttribute("pageTitle", "Standings");
            model.addAttribute("seasonId", payload.seasonId());
            model.addAttribute("viewingRound", payload.viewingRound());
            model.addAttribute("currentRound", payload.currentRound());
            model.addAttribute("lastRound", payload.lastRound());
            model.addAttribute("standings", payload.standings());

            // Return fragment for HTMX requests, full page otherwise
            return isHtmxRequest ? "standings :: standings-content" : "standings";
        });
    }

    private String handleStandingsError(UseCaseError error, Model model, HttpServletResponse response) {
        response.setStatus(ErrorMapper.toHttpStatus(error));
        model.addAttribute("error", error.getMessage());
        model.addAttribute("pageTitle", "Standings");
        return "error";
    }
}
