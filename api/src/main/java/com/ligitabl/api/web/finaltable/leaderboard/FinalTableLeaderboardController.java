package com.ligitabl.api.web.finaltable.leaderboard;

import java.security.Principal;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;

import com.ligitabl.api.auth.security.WebUserDetails;
import com.ligitabl.api.rest.finaltable.leaderboard.GetFinalTableLeaderboardUseCase;
import com.ligitabl.api.rest.finaltable.leaderboard.GetFinalTableLeaderboardUseCase.FinalTableLeaderboardData;
import com.ligitabl.api.web.shared.security.WebSecurity;

import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/** GET /final-table/leaderboard — ranks everyone who entered, once results are revealed. */
@Controller
@RequiredArgsConstructor
@Slf4j
public class FinalTableLeaderboardController {

    private static final int PAGE_SIZE = 10;

    private final GetFinalTableLeaderboardUseCase useCase;

    @GetMapping("/final-table/leaderboard")
    public String leaderboard(
            @RequestParam(required = false, defaultValue = "1") int page,
            Principal principal,
            Model model,
            HttpServletResponse response,
            @RequestHeader(value = "HX-Request", required = false) String hxRequest) {

        WebUserDetails user = WebSecurity.resolveUser(principal);
        int requestedPage = Math.max(1, page);
        int offset = (requestedPage - 1) * PAGE_SIZE;

        return useCase.execute(user == null ? null : user.getUserId(), offset, PAGE_SIZE)
                .fold(
                        error -> {
                            log.warn("GET /final-table/leaderboard unavailable: {}", error);
                            response.setStatus(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
                            model.addAttribute("pageTitle", "Final Table Leaderboard");
                            model.addAttribute("message", "The Final Table leaderboard isn't available right now.");
                            return "final-table-unavailable";
                        },
                        data -> render(data, requestedPage, user, model, hxRequest));
    }

    private String render(
            FinalTableLeaderboardData data, int page, WebUserDetails user, Model model, String hxRequest) {
        int totalPages = Math.max(1, (int) Math.ceil((double) data.totalEntries() / PAGE_SIZE));

        model.addAttribute("pageTitle", "Final Table Leaderboard");
        model.addAttribute("revealed", data.revealed());
        model.addAttribute("leaderboard", data.entries());
        model.addAttribute("displayPositions", data.displayPositions());
        model.addAttribute("seasonShorthand", data.seasonShorthand());
        model.addAttribute("maxScore", data.maxScore());
        model.addAttribute("userEntry", data.userEntry());
        model.addAttribute("currentUserPublicId", user == null ? null : user.getPublicId());
        model.addAttribute("totalEntries", data.totalEntries());
        model.addAttribute("totalPlayers", data.totalPlayers());
        // Pre-reveal only; empty once results are out, where `leaderboard` carries the same people.
        model.addAttribute("entrants", data.entrants());
        model.addAttribute("currentPage", page);
        model.addAttribute("totalPages", totalPages);
        model.addAttribute("pageSize", PAGE_SIZE);
        model.addAttribute("hasPreviousPage", page > 1);
        model.addAttribute("hasNextPage", page < totalPages);

        if (hxRequest != null && !hxRequest.isBlank()) {
            return "final-table-leaderboard :: leaderboardContent";
        }
        return "final-table-leaderboard";
    }
}
