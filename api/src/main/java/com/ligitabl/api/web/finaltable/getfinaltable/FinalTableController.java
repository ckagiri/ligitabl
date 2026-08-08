package com.ligitabl.api.web.finaltable.getfinaltable;

import java.security.Principal;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import com.ligitabl.api.auth.security.WebUserDetails;
import com.ligitabl.api.rest.finaltable.getfinaltable.FinalTableViewData;
import com.ligitabl.api.rest.finaltable.getfinaltable.GetFinalTableUseCase;
import com.ligitabl.api.web.shared.security.WebSecurity;

import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * GET /final-table — the Final Table Predictor page.
 *
 * <p>A guest gets the same table read-only with a sign-up CTA: the game doubles as an on-ramp, so
 * showing the baseline is more useful than an empty state.
 */
@Controller
@RequiredArgsConstructor
@Slf4j
public class FinalTableController {

    private final GetFinalTableUseCase getFinalTableUseCase;

    @GetMapping("/final-table")
    public String finalTable(Principal principal, Model model, HttpServletResponse response) {
        WebUserDetails user = WebSecurity.resolveUser(principal);

        return getFinalTableUseCase
                .execute(user == null ? null : user.getUserId(), user == null ? null : user.getPublicId())
                .fold(error -> renderUnavailable(error, model, response), data -> render(data, model));
    }

    private String render(FinalTableViewData data, Model model) {
        model.addAttribute("pageTitle", "Final Table Predictor");
        model.addAttribute("rankings", data.rankings());
        model.addAttribute("teamsByCode", data.teamsByCode());
        model.addAttribute("entryOpen", data.entryOpen());
        model.addAttribute("hasEntry", data.hasEntry());
        model.addAttribute("revealed", data.revealed());
        model.addAttribute("expectedOrder", data.expectedOrder());
        model.addAttribute("swapCount", data.swapCount());
        model.addAttribute("settledAt", data.settledAt());
        model.addAttribute("resultRankings", data.resultRankings());
        model.addAttribute("baseScore", data.baseScore());
        model.addAttribute("zeroesCount", data.zeroesCount());
        model.addAttribute("bonusPoints", data.bonusPoints());
        model.addAttribute("totalScore", data.totalScore());
        model.addAttribute("roundStatus", data.roundStatus());
        model.addAttribute("shareUrl", data.shareUrl());
        model.addAttribute("shareText", data.shareText());
        model.addAttribute("isGuest", data.isGuest());
        model.addAttribute("devPreviewEnabled", data.devPreviewEnabled());
        model.addAttribute("readOnly", false);
        return "final-table";
    }

    /** No active season is a service-state problem, not a user error — say so rather than 404. */
    private String renderUnavailable(Object error, Model model, HttpServletResponse response) {
        log.warn("GET /final-table unavailable: {}", error);
        response.setStatus(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
        model.addAttribute("pageTitle", "Final Table Predictor");
        model.addAttribute("message", "The Final Table Predictor isn't available right now.");
        return "final-table-unavailable";
    }
}
