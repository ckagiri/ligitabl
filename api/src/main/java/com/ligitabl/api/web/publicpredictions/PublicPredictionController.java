package com.ligitabl.api.web.publicpredictions;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ligitabl.api.config.CompetitionDefaults;
import com.ligitabl.api.rest.standings.FormService;
import com.ligitabl.api.web.shared.fixtures.FixtureJsonMapper;
import com.ligitabl.api.web.shared.swap.SwapHistoryFormatter;
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
    private final FormService formService;
    private final FixtureJsonMapper fixtureJsonMapper;
    private final ObjectMapper objectMapper;
    private final SwapHistoryFormatter swapHistoryFormatter;

    /**
     * GET /u/{publicId}/gw — resolves the active season's current round and redirects to the
     * canonical URL. Doesn't depend on the target user at all, so no use case call needed.
     */
    @GetMapping("/u/{publicId}/gw")
    public String redirectToCurrentRound(@PathVariable String publicId, Model model, HttpServletResponse response) {
        Optional<Competition> competitionOpt = competitionRepo.findBySlug(competitionDefaults.defaultCompetitionSlug());
        if (competitionOpt.isEmpty()) {
            return notFound(model, response, "Competition not found");
        }

        Optional<Season> seasonOpt =
                seasonRepo.findActiveSeason(competitionOpt.get().getId());
        if (seasonOpt.isEmpty()) {
            return notFound(model, response, "No active season available");
        }
        Season season = seasonOpt.get();

        Optional<Round> currentRoundOpt = roundRepo.findById(season.getCurrentRoundId());
        if (currentRoundOpt.isEmpty()) {
            return notFound(model, response, "Current round not found");
        }

        return "redirect:"
                + canonicalUrl(publicId, season.getSlug(), currentRoundOpt.get().getPosition());
    }

    /**
     * GET /u/{publicId}/{season} and /u/{publicId}/{season}/gw — same landing behaviour as
     * {@code /u/{publicId}/gw}, but for an explicitly named season rather than the active one:
     * resolve that season's current round and redirect to the canonical URL, so a link without a
     * round still opens on something.
     *
     * <p>{@code /u/{publicId}/gw} is a more specific pattern than {@code /u/{publicId}/{season}},
     * so Spring keeps routing it to the active-season handler above rather than treating "gw" as a
     * season slug.
     */
    @GetMapping({"/u/{publicId}/{season}", "/u/{publicId}/{season}/gw"})
    public String redirectToSeasonCurrentRound(
            @PathVariable String publicId, @PathVariable String season, Model model, HttpServletResponse response) {
        SeasonSlug seasonSlug;
        try {
            seasonSlug = SeasonSlug.fromShorthand(season);
        } catch (IllegalArgumentException e) {
            return notFound(model, response, "Invalid season: " + season);
        }

        Optional<Competition> competitionOpt = competitionRepo.findBySlug(competitionDefaults.defaultCompetitionSlug());
        if (competitionOpt.isEmpty()) {
            return notFound(model, response, "Competition not found");
        }

        Optional<Season> seasonOpt =
                seasonRepo.findByCompetitionIdAndSlug(competitionOpt.get().getId(), seasonSlug);
        if (seasonOpt.isEmpty()) {
            return notFound(model, response, "Season not found: " + season);
        }
        Season resolvedSeason = seasonOpt.get();

        Optional<Round> currentRoundOpt = roundRepo.findById(resolvedSeason.getCurrentRoundId());
        if (currentRoundOpt.isEmpty()) {
            return notFound(model, response, "Current round not found");
        }

        return "redirect:"
                + canonicalUrl(
                        publicId,
                        resolvedSeason.getSlug(),
                        currentRoundOpt.get().getPosition());
    }

    @GetMapping("/u/{publicId}/{season}/gw/{position}")
    public String publicPrediction(
            @PathVariable String publicId,
            @PathVariable String season,
            @PathVariable int position,
            @RequestParam(required = false) String from,
            Model model,
            HttpServletResponse response,
            @RequestHeader(value = "HX-Request", required = false) String hxRequest) {
        log.info("GET /u/{}/{}/gw/{}", publicId, season, position);

        model.addAttribute("backHref", resolveBackHref(from));

        SeasonSlug seasonSlug;
        try {
            seasonSlug = SeasonSlug.fromShorthand(season);
        } catch (IllegalArgumentException e) {
            return notFound(model, response, "Invalid season: " + season);
        }

        Optional<Competition> competitionOpt = competitionRepo.findBySlug(competitionDefaults.defaultCompetitionSlug());
        if (competitionOpt.isEmpty()) {
            return notFound(model, response, "Competition not found");
        }

        Optional<Season> seasonOpt =
                seasonRepo.findByCompetitionIdAndSlug(competitionOpt.get().getId(), seasonSlug);
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
                        data -> handleSuccess(
                                publicId, resolvedSeason, competitionName, position, data, from, model, hxRequest));
    }

    private String resolveBackHref(String from) {
        if (from == null || from.isBlank()) return null;

        if ("leaderboard".equals(from)) return "/leaderboard";

        if (from.startsWith("contest:")) {
            String contestId = from.substring("contest:".length());
            try {
                return "/contests/" + UUID.fromString(contestId);
            } catch (IllegalArgumentException e) {
                log.warn("Ignoring malformed contest id in `from` marker: {}", from);
                return null;
            }
        }

        log.warn("Ignoring unrecognised `from` marker: {}", from);
        return null;
    }

    private String handleSuccess(
            String publicId,
            Season season,
            String competitionName,
            int requestedPosition,
            PublicPredictionViewData data,
            String from,
            Model model,
            String hxRequest) {
        // The use case clamps out-of-range requests to the target user's valid bounds — redirect
        // to the canonical URL for the clamped round rather than silently rendering a different
        // round at the requested URL.
        if (data.viewingRound() != requestedPosition) {
            return "redirect:" + canonicalUrl(publicId, season.getSlug(), data.viewingRound(), from);
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
        model.addAttribute("maxHitPoints", season.getMaxHitPoints());
        model.addAttribute("swapHistory", swapHistoryFormatter.format(data.roundSwaps()));
        model.addAttribute(
                "navBaseUrl", "/u/" + publicId + "/" + season.getSlug().toShorthand());
        // Re-set on this path too: the model attribute set before the use case call is lost on the
        // clamp redirect, and round navigation needs `from` to keep the Back link alive.
        String backHref = resolveBackHref(from);
        model.addAttribute("backHref", backHref);
        // Only propagate `from` through round nav once it resolved to a real destination, so a junk
        // marker doesn't get carried from URL to URL.
        model.addAttribute(
                "navQueryString", backHref != null ? "from=" + URLEncoder.encode(from, StandardCharsets.UTF_8) : null);

        // The richer comparison-options view (points/GD/fixtures/form) only applies to the
        // current/live round — historical rounds keep the plain static table, no JSON needed.
        if (!data.hasRoundResult()) {
            populateComparisonOptionsModel(season, data, model);
        }

        if (hxRequest != null && !hxRequest.isBlank()) {
            return "public-predictions :: publicPredictionPage";
        }
        return "public-predictions";
    }

    /**
     * JSON blobs + toggle state for the current-round comparison-options view (points/GD/
     * fixtures/form) — mirrors {@code UserPredictionsController}'s JSON serialization for the
     * owner's own current-round table.
     */
    private void populateComparisonOptionsModel(Season season, PublicPredictionViewData data, Model model) {
        var formMap = formService.buildFormMap(season.getId(), data.viewingRound());
        model.addAttribute("hasFormData", !formMap.isEmpty());
        model.addAttribute("roundId", data.viewingRound());

        try {
            model.addAttribute("predictionsJson", objectMapper.writeValueAsString(data.rows()));
            model.addAttribute(
                    "fixturesJson", objectMapper.writeValueAsString(fixtureJsonMapper.buildFixtures(data.matches())));
            model.addAttribute("currentStandingsJson", objectMapper.writeValueAsString(data.standingsMap()));
            model.addAttribute("currentPointsJson", objectMapper.writeValueAsString(data.pointsMap()));
            model.addAttribute("currentGoalDifferenceJson", objectMapper.writeValueAsString(data.goalDifferenceMap()));
            model.addAttribute("formJson", objectMapper.writeValueAsString(formMap));
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize public prediction comparison data", e);
            model.addAttribute("predictionsJson", "[]");
            model.addAttribute("fixturesJson", "{}");
            model.addAttribute("currentStandingsJson", "{}");
            model.addAttribute("currentPointsJson", "{}");
            model.addAttribute("currentGoalDifferenceJson", "{}");
            model.addAttribute("formJson", "{}");
        }
    }

    private String handleError(GetPublicPredictionUseCase.Error error, Model model, HttpServletResponse response) {
        String message =
                switch (error) {
                    case GetPublicPredictionUseCase.Error.SeasonNotFound e -> "Season not found: " + e.seasonId();
                    case GetPublicPredictionUseCase.Error.CurrentRoundNotFound
                    e -> "Current round not found for season: " + e.seasonId();
                };
        return notFound(model, response, message);
    }

    private String notFound(Model model, HttpServletResponse response, String message) {
        response.setStatus(404);
        model.addAttribute("error", message);
        return "error";
    }

    private String canonicalUrl(String publicId, SeasonSlug seasonSlug, int position) {
        return canonicalUrl(publicId, seasonSlug, position, null);
    }

    /** Canonical URL, preserving the {@code from} back-link marker across redirects. */
    private String canonicalUrl(String publicId, SeasonSlug seasonSlug, int position, String from) {
        String url = "/u/" + publicId + "/" + seasonSlug.toShorthand() + "/gw/" + position;
        if (from != null && !from.isBlank()) {
            url += "?from=" + URLEncoder.encode(from, StandardCharsets.UTF_8);
        }
        return url;
    }

    private String pageTitle(PublicPredictionViewData data, String competitionName) {
        return data.targetDisplayName() != null
                ? data.targetDisplayName() + "'s " + competitionName + " Table"
                : competitionName + " Table";
    }
}
