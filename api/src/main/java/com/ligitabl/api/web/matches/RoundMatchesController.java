package com.ligitabl.api.web.matches;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;

import com.ligitabl.api.rest.match.getdefaultroundmatches.GetDefaultRoundMatchesQuery;
import com.ligitabl.api.rest.match.getdefaultroundmatches.GetDefaultRoundMatchesUseCase;
import com.ligitabl.api.shared.errors.UseCaseError;
import com.ligitabl.api.web.shared.error.ErrorMapper;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Controller
@RequiredArgsConstructor
@Slf4j
@RequestMapping("/rounds")
public class RoundMatchesController {
    private final GetDefaultRoundMatchesUseCase getDefaultRoundMatchesUseCase;

    /**
     * GET /rounds/current/matches - View current round matches
     */
    @GetMapping("/current/matches")
    public String getCurrentRoundMatches(Model model, HttpServletRequest request, HttpServletResponse response) {
        log.info("GET /rounds/current/matches");
        return executeUseCase(GetDefaultRoundMatchesQuery.currentRound(), model, request, response);
    }

    /**
     * GET /rounds/{roundPosition}/matches - View specific round matches
     */
    @GetMapping("/{roundPosition}/matches")
    public String getRoundMatchesByPosition(
            @PathVariable Integer roundPosition,
            Model model,
            HttpServletRequest request,
            HttpServletResponse response) {
        log.info("GET /rounds/{}/matches", roundPosition);
        return executeUseCase(GetDefaultRoundMatchesQuery.byPosition(roundPosition, null), model, request, response);
    }

    private String executeUseCase(
            GetDefaultRoundMatchesQuery query, Model model, HttpServletRequest request, HttpServletResponse response) {
        var result = getDefaultRoundMatchesUseCase.execute(query);

        // Check if this is an HTMX request
        boolean isHtmxRequest = "true".equals(request.getHeader("HX-Request"));

        return result.fold(error -> handleMatchesError(error, model, response), payload -> {
            model.addAttribute("pageTitle", "Matches");
            model.addAttribute("seasonId", payload.seasonId());
            model.addAttribute("viewingRound", payload.viewingRound());
            model.addAttribute("currentRound", payload.currentRound());
            model.addAttribute("lastRound", payload.lastRound());
            model.addAttribute("matches", payload.matches());

            boolean isViewingCurrentRound = payload.viewingRound() == payload.currentRound();
            boolean allMatchesTerminal = !payload.matches().isEmpty()
                    && payload.matches().stream()
                            .allMatch(m -> "FINISHED".equals(m.getStatus()) || "POSTPONED".equals(m.getStatus()));
            boolean allMatchesFinished = !payload.matches().isEmpty()
                    && payload.matches().stream().allMatch(m -> "FINISHED".equals(m.getStatus()));

            model.addAttribute("canFinalizeRound",
                    isViewingCurrentRound && allMatchesTerminal && !payload.roundFinalized());
            model.addAttribute("showRefreshButton",
                    isViewingCurrentRound && !payload.roundFinalized() && !allMatchesFinished);

            // Return fragment for HTMX requests, full page otherwise
            return isHtmxRequest ? "matches :: matches-content" : "matches";
        });
    }

    private String handleMatchesError(UseCaseError error, Model model, HttpServletResponse response) {
        response.setStatus(ErrorMapper.toHttpStatus(error));
        model.addAttribute("error", error.getMessage());
        model.addAttribute("pageTitle", "Matches");
        return "error";
    }
}
