package com.ligitabl.api.web.leaderboard;

import java.util.List;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Controller
@RequestMapping("/leaderboard")
@RequiredArgsConstructor
@Slf4j
public class GetUserDetailController {

    private final GetUserDetailUseCase getUserDetailUseCase;

    @PostMapping("/user/modal")
    public String getUserModal(
            @RequestParam String publicId,
            @RequestParam String displayName,
            @RequestParam int position,
            @RequestParam int totalScore,
            @RequestParam int roundScore,
            @RequestParam int totalSwaps,
            @RequestParam int movement,
            @RequestParam(required = false) Integer effectiveToRound,
            Model model,
            HttpServletResponse response) {

        log.info("POST /leaderboard/user/modal - publicId: {}", publicId);

        // Fetch predictions
        return getUserDetailUseCase
                .execute(publicId, effectiveToRound)
                .fold(
                        error -> handleError(error, model, response),
                result -> handleSuccess(result, displayName, position, totalScore, roundScore, model));
    }

    private String handleSuccess(
            GetUserDetailUseCase.UserPredictions result,
            String displayName,
            int position,
            int totalScore,
            int roundScore,
            Model model) {

        int roundZeroes = (int) result.predictions().stream()
            .filter(pred -> pred.hit() != null && pred.hit() == 0)
            .count();

        // Create user DTO with combined leaderboard + prediction data
        var user = new UserDetailDTO(displayName, position, totalScore, roundScore, roundZeroes, result.predictions());

        model.addAttribute("user", user);
        model.addAttribute("round", result.round());
        model.addAttribute("status", ""); // Always show current round predictions

        return "fragments/user-detail :: user-details(user=${user}, round=${round}, status=${status})";
    }

    private String handleError(Exception error, Model model, HttpServletResponse response) {
        log.error("Error fetching user predictions: {}", error.getMessage());

        response.setStatus(404);
        model.addAttribute("error", error.getMessage());
        return "fragments/error-banner :: banner";
    }

    private record UserDetailDTO(
            String displayName,
            int position,
            int totalScore,
            int roundScore,
            int roundZeroes,
            List<GetUserDetailUseCase.PredictionTeam> currentPrediction) {}
}
