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
            model.addAttribute("seasonSlug", payload.seasonSlug());
            model.addAttribute("viewingRound", payload.viewingRound());
            model.addAttribute("currentRound", payload.currentRound());
            model.addAttribute("lastRound", payload.lastRound());
            model.addAttribute("matches", payload.matches());
            model.addAttribute("seasonInSetupMode", payload.seasonInSetupMode());

            boolean isViewingCurrentRound = payload.viewingRound() == payload.currentRound();
            boolean hasNonTerminalMatches = !payload.matches().isEmpty()
                    && payload.matches().stream()
                            .anyMatch(m -> !"FINISHED".equals(m.getStatus()) && !"POSTPONED".equals(m.getStatus()));

            // A viewed past round whose standings aren't finalized is out of sync — this can
            // only really happen after a setup-mode refinalize cascade marked it that way.
            // Future rounds are excluded: they're never finalized simply because they haven't happened yet.
            // Matches not being complete (e.g. a match temporarily reverted to SCHEDULED in setup
            // mode) is itself a sign the round is out of sync with the season's progression.
            boolean isPastRound = payload.viewingRound() < payload.currentRound();
            boolean isOutOfSync = isPastRound && (!payload.standingsFinalised() || !payload.matchesComplete());
            boolean canRefinalizeRound = payload.seasonInSetupMode()
                    && payload.allMatchesTerminalOrBlocking()
                    && (payload.roundFinalized() || isOutOfSync);
            // Finalize and Refinalize must never both be true for the same render — Finalize only
            // ever applies to the current, not-yet-finalized round; make that exclusion explicit.
            // The season's last round requires every match to genuinely be FINISHED (not merely
            // terminal-or-blocking, i.e. postponed/cancelled/suspended) before it can be finalized
            // — there's no future round left to carry over a postponed match into.
            boolean isLastRound = payload.viewingRound() == payload.lastRound();
            boolean matchesReadyToFinalize =
                    isLastRound ? payload.allMatchesFinished() : payload.allMatchesTerminalOrBlocking();
            boolean canFinalizeRound = isViewingCurrentRound
                    && matchesReadyToFinalize
                    && !payload.roundFinalized()
                    && !canRefinalizeRound;
            boolean canAdvanceRound =
                    isViewingCurrentRound && payload.roundFinalized() && !payload.roundAdvanced();
            boolean canCancelAutoAdvancement = canAdvanceRound && payload.autoAdvanceScheduled();
            boolean canCompleteSeason = isViewingCurrentRound
                    && payload.viewingRound() == payload.lastRound()
                    && payload.roundFinalized()
                    && payload.roundAdvanced()
                    && !payload.seasonCompleted();

            model.addAttribute("isViewingCurrentRound", isViewingCurrentRound);
            model.addAttribute("isOutOfSync", isOutOfSync);
            model.addAttribute("canRefinalizeRound", canRefinalizeRound);
            model.addAttribute("canFinalizeRound", canFinalizeRound);
            model.addAttribute("canAdvanceRound", canAdvanceRound);
            model.addAttribute("canCancelAutoAdvancement", canCancelAutoAdvancement);
            model.addAttribute("canCompleteSeason", canCompleteSeason);
            model.addAttribute(
                    "showRefreshButton", isViewingCurrentRound && !payload.roundFinalized() && hasNonTerminalMatches);
            model.addAttribute("nextRoundQuery", nextRoundAfterViewing(payload.viewingRound(), payload.currentRound()));

            // Return fragment for HTMX requests, full page otherwise
            return isHtmxRequest ? "matches :: matches-content" : "matches";
        });
    }

    private Integer nextRoundAfterViewing(int viewingRound, int currentRound) {
        int next = viewingRound + 1;
        return next == currentRound ? null : next; // null -> land on /rounds/current/matches
    }

    private String handleMatchesError(UseCaseError error, Model model, HttpServletResponse response) {
        response.setStatus(ErrorMapper.toHttpStatus(error));
        model.addAttribute("error", error.getMessage());
        model.addAttribute("pageTitle", "Matches");
        return "error";
    }
}
