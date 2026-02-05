package com.ligitabl.api.web.match;

import com.ligitabl.api.rest.match.getdefaultroundmatches.GetDefaultRoundMatchesQuery;
import com.ligitabl.api.rest.match.getdefaultroundmatches.GetDefaultRoundMatchesUseCase;
import com.ligitabl.api.shared.errors.UseCaseError;
import com.ligitabl.api.web.shared.error.UseCaseErrorStatusMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
@RequiredArgsConstructor
@Slf4j
@RequestMapping("/rounds")
public class RoundMatchesController {
    private final GetDefaultRoundMatchesUseCase getDefaultRoundMatchesUseCase;

    @GetMapping("/current/matches")
    public String getCurrentRoundMatches(
            @RequestParam(required = false) Integer round,
            Model model,
            HttpServletRequest request,
            HttpServletResponse response) {
        log.info("GetCurrentRoundMatches command, round={}", round);
        GetDefaultRoundMatchesQuery query = round == null
                ? GetDefaultRoundMatchesQuery.currentRound()
                : GetDefaultRoundMatchesQuery.byPosition(round, null);

        return executeUseCase(query, model, request, response);
    }

    @GetMapping("/{roundPosition}/matches")
    public String getRoundMatchesByPosition(
            @PathVariable String roundPosition,
            Model model,
            HttpServletRequest request,
            HttpServletResponse response) {
        log.info("GetRoundMatches command, position={}", roundPosition);
        Integer parsedRound;
        try {
            parsedRound = GetDefaultRoundMatchesQuery.parseRoundPosition(roundPosition);
        } catch (IllegalArgumentException ex) {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            model.addAttribute("pageTitle", "Matches");
            model.addAttribute("error", ex.getMessage());
            return "error";
        }

        GetDefaultRoundMatchesQuery query = parsedRound == null
                ? GetDefaultRoundMatchesQuery.currentRound()
                : GetDefaultRoundMatchesQuery.byPosition(parsedRound, null);

        return executeUseCase(query, model, request, response);
    }

    private String executeUseCase(GetDefaultRoundMatchesQuery query, Model model, HttpServletRequest request, HttpServletResponse response) {
        var result = getDefaultRoundMatchesUseCase.execute(query);

        // Check if this is an HTMX request
        boolean isHtmxRequest = "true".equals(request.getHeader("HX-Request"));

        return result.fold(
                error -> handleMatchesError(error, model, response),
                payload -> {
                    model.addAttribute("pageTitle", "Matches");
                    model.addAttribute("seasonId", payload.seasonId());
                    model.addAttribute("viewingRound", payload.viewingRound());
                    model.addAttribute("currentRound", payload.currentRound());
                    model.addAttribute("lastRound", payload.lastRound());
                    model.addAttribute("matches", payload.matches());

                    // Return fragment for HTMX requests, full page otherwise
                    return isHtmxRequest ? "matches :: matches-content" : "matches";
                });
    }

    private String handleMatchesError(UseCaseError error, Model model, HttpServletResponse response) {
        response.setStatus(UseCaseErrorStatusMapper.toHttpStatus(error));
        model.addAttribute("error", error.getMessage());
        model.addAttribute("pageTitle", "Matches");
        return "error";
    }
}
