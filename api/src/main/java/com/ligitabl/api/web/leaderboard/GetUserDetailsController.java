package com.ligitabl.api.web.leaderboard;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import com.ligitabl.api.rest.leaderboard.getuserdetails.GetUserDetailsError;
import com.ligitabl.api.rest.leaderboard.getuserdetails.GetUserDetailsQuery;
import com.ligitabl.api.rest.leaderboard.getuserdetails.GetUserDetailsResult;
import com.ligitabl.api.rest.leaderboard.getuserdetails.GetUserDetailsUseCase;

import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Controller
@RequestMapping("/leaderboard")
@RequiredArgsConstructor
@Slf4j
public class GetUserDetailsController {
    private final GetUserDetailsUseCase getUserDetailUseCase;

    @GetMapping("/user/{userId}/details")
    public String userDetails(
            @PathVariable String userId,
            @RequestParam(required = false) String phase,
            Model model,
            HttpServletResponse response) {

        log.info("GET /leaderboard/user/{}/details - phase: {}", userId, phase);

        var query = new GetUserDetailsQuery(userId, phase);

        return getUserDetailUseCase
                .execute(query)
                .fold(error -> handleError(error, model, response), result -> handleSuccess(result, model));
    }

    private String handleSuccess(GetUserDetailsResult result, Model model) {
        model.addAttribute("user", result);
        model.addAttribute("round", result.effectiveRound());
        model.addAttribute("status", result.showingPreviousRound() ? "Finalised" : "");

        return "fragments/user-detail :: user-details(user=${user}, round=${round}, status=${status})";
    }

    private String handleError(GetUserDetailsError error, Model model, HttpServletResponse response) {
        ErrorResponse errorResponse =
                switch (error) {
                    case GetUserDetailsError.UserNotFound __ -> new ErrorResponse(404, "User not found");
                    case GetUserDetailsError.NoFinalizedRounds __ -> new ErrorResponse(404, "No finalized rounds yet");
                    case GetUserDetailsError.CurrentRoundNotFound __ -> new ErrorResponse(
                            404, "Current round not found");
                    case GetUserDetailsError.NoPredictionFound e -> new ErrorResponse(
                            404, "No prediction found for round " + e.round());
                    case GetUserDetailsError.LeaderboardError __ -> new ErrorResponse(
                            500, "Could not load leaderboard");
                };

        response.setStatus(errorResponse.status());
        model.addAttribute("error", errorResponse.message());
        return "fragments/error-banner :: banner";
    }

    private record ErrorResponse(int status, String message) {}
}
