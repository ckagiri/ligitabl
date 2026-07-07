package com.ligitabl.api.web.publicpredictions;

import java.util.Optional;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;

import com.ligitabl.api.config.CompetitionDefaults;
import com.ligitabl.model.domain.Competition;
import com.ligitabl.model.domain.Round;
import com.ligitabl.model.domain.Season;
import com.ligitabl.model.domain.SeasonSlug;
import com.ligitabl.model.repo.CompetitionRepo;
import com.ligitabl.model.repo.RoundRepo;
import com.ligitabl.model.repo.SeasonRepo;

import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Public, no-login prediction sharing view: {@code /u/{publicId}/{season}/gw/{position}}.
 * Always read-only — anyone with the link can view (never edit) the target user's table.
 */
@Controller
@RequiredArgsConstructor
@Slf4j
public class PublicPredictionController {
    private final CompetitionRepo competitionRepo;
    private final SeasonRepo seasonRepo;
    private final RoundRepo roundRepo;
    private final CompetitionDefaults competitionDefaults;
    private final GetPublicPredictionUseCase getPublicPredictionUseCase;

    /**
     * GET /u/{publicId}/gw — resolves the active season's current round and redirects to the
     * canonical URL. Doesn't depend on the target user at all, so no use case call needed.
     */
    @GetMapping("/u/{publicId}/gw")
    public String redirectToCurrentRound(@PathVariable String publicId, Model model, HttpServletResponse response) {
        Optional<Competition> competitionOpt =
                competitionRepo.findBySlug(competitionDefaults.defaultCompetitionSlug());
        if (competitionOpt.isEmpty()) {
            return notFound(model, response, "Competition not found");
        }

        Optional<Season> seasonOpt = seasonRepo.findActiveSeason(competitionOpt.get().getId());
        if (seasonOpt.isEmpty()) {
            return notFound(model, response, "No active season available");
        }
        Season season = seasonOpt.get();

        Optional<Round> currentRoundOpt = roundRepo.findById(season.getCurrentRoundId());
        if (currentRoundOpt.isEmpty()) {
            return notFound(model, response, "Current round not found");
        }

        return "redirect:" + canonicalUrl(publicId, season.getSlug(), currentRoundOpt.get().getPosition());
    }

    @GetMapping("/u/{publicId}/{season}/gw/{position}")
    public String publicPrediction(
            @PathVariable String publicId,
            @PathVariable String season,
            @PathVariable int position,
            Model model,
            HttpServletResponse response,
            @RequestHeader(value = "HX-Request", required = false) String hxRequest) {
        log.info("GET /u/{}/{}/gw/{}", publicId, season, position);

        SeasonSlug seasonSlug;
        try {
            seasonSlug = SeasonSlug.fromShorthand(season);
        } catch (IllegalArgumentException e) {
            return notFound(model, response, "Invalid season: " + season);
        }

        Optional<Competition> competitionOpt =
                competitionRepo.findBySlug(competitionDefaults.defaultCompetitionSlug());
        if (competitionOpt.isEmpty()) {
            return notFound(model, response, "Competition not found");
        }

        Optional<Season> seasonOpt = seasonRepo.findByCompetitionIdAndSlug(competitionOpt.get().getId(), seasonSlug);
        if (seasonOpt.isEmpty()) {
            return notFound(model, response, "Season not found: " + season);
        }
        Season resolvedSeason = seasonOpt.get();
        String competitionName = competitionOpt.get().getName();

        var query = new GetPublicPredictionQuery(publicId, resolvedSeason.getId(), position);

        return getPublicPredictionUseCase
                .execute(query)
                .fold(
                        error -> handleError(error, model, response),
                        data -> handleSuccess(publicId, resolvedSeason, competitionName, position, data, model, hxRequest));
    }

    private String handleSuccess(
            String publicId,
            Season season,
            String competitionName,
            int requestedPosition,
            PublicPredictionViewData data,
            Model model,
            String hxRequest) {
        // The use case clamps out-of-range requests to the target user's valid bounds — redirect
        // to the canonical URL for the clamped round rather than silently rendering a different
        // round at the requested URL.
        if (data.viewingRound() != requestedPosition) {
            return "redirect:" + canonicalUrl(publicId, season.getSlug(), data.viewingRound());
        }

        model.addAttribute("pageTitle", pageTitle(data, competitionName));
        model.addAttribute("competitionName", competitionName);
        model.addAttribute(
                "possessiveDisplayName", data.targetDisplayName() != null ? data.targetDisplayName() + "'s" : null);
        model.addAttribute("viewingRound", data.viewingRound());
        model.addAttribute("currentRound", data.currentRound());
        model.addAttribute("minRound", data.minRound());
        model.addAttribute("userFound", data.userFound());
        model.addAttribute("targetDisplayName", data.targetDisplayName());
        model.addAttribute("rows", data.rows());
        model.addAttribute("hasRoundResult", data.hasRoundResult());
        model.addAttribute("totalScore", data.totalScore());
        model.addAttribute("totalHits", data.totalHits());
        model.addAttribute("zeroesCount", data.zeroesCount());
        model.addAttribute("navBaseUrl", "/u/" + publicId + "/" + season.getSlug().toShorthand());

        if (hxRequest != null && !hxRequest.isBlank()) {
            return "public-predictions :: publicPredictionPage";
        }
        return "public-predictions";
    }

    private String handleError(GetPublicPredictionUseCase.Error error, Model model, HttpServletResponse response) {
        String message =
                switch (error) {
                    case GetPublicPredictionUseCase.Error.SeasonNotFound e -> "Season not found: " + e.seasonId();
                    case GetPublicPredictionUseCase.Error.CurrentRoundNotFound e ->
                        "Current round not found for season: " + e.seasonId();
                };
        return notFound(model, response, message);
    }

    private String notFound(Model model, HttpServletResponse response, String message) {
        response.setStatus(404);
        model.addAttribute("error", message);
        return "error";
    }

    private String canonicalUrl(String publicId, SeasonSlug seasonSlug, int position) {
        return "/u/" + publicId + "/" + seasonSlug.toShorthand() + "/gw/" + position;
    }

    private String pageTitle(PublicPredictionViewData data, String competitionName) {
        return data.targetDisplayName() != null
                ? data.targetDisplayName() + "'s " + competitionName + " Table"
                : competitionName + " Table";
    }
}
