package com.ligitabl.api.web.leaderboard;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

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
            @RequestParam(required = false) Integer effectiveToRound,
            @RequestParam(required = false) Integer phaseFrom,
            @RequestParam(required = false) Integer maxRound,
            @RequestParam(required = false, defaultValue = "true") boolean scored,
            @RequestParam(required = false) UUID seasonId,
            @RequestParam(required = false) String from,
            Model model,
            HttpServletResponse response) {

        log.info("POST /leaderboard/user/modal - publicId: {}", publicId);

        return getUserDetailUseCase
                .execute(publicId, effectiveToRound, seasonId)
                .fold(
                        error -> handleError(error, model, response),
                        result -> handleSuccess(
                                result,
                                publicId,
                                displayName,
                                position,
                                totalScore,
                                roundScore,
                                effectiveToRound,
                                phaseFrom,
                                maxRound,
                                scored,
                                seasonId,
                                from,
                                model));
    }

    private String handleSuccess(
            GetUserDetailUseCase.UserPredictions result,
            String publicId,
            String displayName,
            int position,
            int totalScore,
            int requestRoundScore,
            Integer effectiveToRound,
            Integer phaseFrom,
            Integer maxRoundParam,
            boolean scored,
            UUID seasonId,
            String from,
            Model model) {

        int roundScore = result.roundScore() != null
                ? result.roundScore()
                : (result.predictions().isEmpty() ? 0 : requestRoundScore);

        int roundZeroes = (int) result.predictions().stream()
                .filter(pred -> pred.hit() != null && pred.hit() == 0)
                .count();

        var user = new UserDetailDTO(
                publicId, displayName, position, totalScore, roundScore, roundZeroes, result.predictions());

        int minRound = phaseFrom != null ? phaseFrom : 1;
        int maxRound =
                maxRoundParam != null ? maxRoundParam : (effectiveToRound != null ? effectiveToRound : result.round());

        model.addAttribute("user", user);
        model.addAttribute("round", result.round());
        model.addAttribute("minRound", minRound);
        model.addAttribute("maxRound", maxRound);
        model.addAttribute("scored", scored);
        model.addAttribute("seasonId", seasonId);
        model.addAttribute("from", from);
        model.addAttribute(
                "publicPredictionHref", publicPredictionHref(publicId, result.seasonSlug(), result.round(), from));
        // Shown as the link's visible text: the clean canonical path, without the `from` marker
        // that the href carries for the target page's Back link.
        model.addAttribute("publicPredictionPath", publicPredictionPath(publicId, result.seasonSlug(), result.round()));

        return "fragments/user-detail :: user-details";
    }

    /** Canonical public-prediction path, no query string — used as the link's visible label. */
    private String publicPredictionPath(String publicId, String seasonSlug, int round) {
        if (seasonSlug == null) return null;

        return "/u/" + publicId + "/" + seasonSlug + "/gw/" + round;
    }

    private String publicPredictionHref(String publicId, String seasonSlug, int round, String from) {
        if (seasonSlug == null) return null;

        String href = "/u/" + URLEncoder.encode(publicId, StandardCharsets.UTF_8) + "/" + seasonSlug + "/gw/" + round;
        if (from != null && !from.isBlank()) {
            href += "?from=" + URLEncoder.encode(from, StandardCharsets.UTF_8);
        }
        return href;
    }

    private String handleError(Exception error, Model model, HttpServletResponse response) {
        log.error("Error fetching user predictions: {}", error.getMessage());

        response.setStatus(404);
        model.addAttribute("error", error.getMessage());
        return "fragments/error-banner :: banner";
    }

    private record UserDetailDTO(
            String publicId,
            String displayName,
            int position,
            int totalScore,
            int roundScore,
            int roundZeroes,
            List<GetUserDetailUseCase.PredictionTeam> currentPrediction) {}
}
