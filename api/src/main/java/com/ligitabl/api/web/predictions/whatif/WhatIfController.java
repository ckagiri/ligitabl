package com.ligitabl.api.web.predictions.whatif;

import java.time.Clock;
import java.time.Instant;
import java.security.Principal;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ligitabl.api.auth.CurrentUserPublicId;
import com.ligitabl.api.auth.security.WebUserDetails;
import com.ligitabl.api.config.CompetitionDefaults;
import com.ligitabl.api.rest.prediction.whatif.ComputeWhatIfUseCase;
import com.ligitabl.api.rest.prediction.whatif.SaveWhatIfPredictionUseCase;
import com.ligitabl.api.rest.prediction.whatif.WhatIfCommand;
import com.ligitabl.api.rest.prediction.whatif.WhatIfError;
import com.ligitabl.api.rest.prediction.whatif.WhatIfResult;
import com.ligitabl.api.rest.prediction.whatif.WhatIfScore;
import com.ligitabl.api.rest.round.shared.RoundSupport;
import com.ligitabl.api.shared.Either;
import com.ligitabl.api.shared.errors.UseCaseError;
import com.ligitabl.api.web.predictions.userpredictions.GetUserPredictionQuery;
import com.ligitabl.api.web.predictions.userpredictions.GetUserPredictionUseCase;
import com.ligitabl.api.web.predictions.userpredictions.UserPredictionViewData;
import com.ligitabl.api.web.shared.dto.TeamRankDto;
import com.ligitabl.api.web.shared.security.WebSecurity;
import com.ligitabl.model.auth.PublicId;
import com.ligitabl.model.domain.Match;
import com.ligitabl.model.domain.Round;
import com.ligitabl.model.domain.RoundStatus;
import com.ligitabl.model.domain.Season;
import com.ligitabl.model.domain.Team;
import com.ligitabl.model.domain.TeamRank;
import com.ligitabl.model.domain.WhatIfPrediction;
import com.ligitabl.model.repo.ContestRepo;
import com.ligitabl.model.repo.MatchRepo;
import com.ligitabl.model.repo.SeasonRepo;
import com.ligitabl.model.repo.TeamRepo;
import com.ligitabl.model.repo.WhatIfPredictionRepo;

import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * What-if score simulator: enter hypothetical scores for the current round's matches, apply, and
 * explore a sandbox team order against the resulting projected standings. Nothing here writes to
 * the real {@code SeasonPrediction} — see {@link ComputeWhatIfUseCase} for the read-only simulation.
 */
@Controller
@Slf4j
@RequiredArgsConstructor
@RequestMapping("/predictions/user/what-if")
public class WhatIfController {

    private final GetUserPredictionUseCase getUserPredictionUseCase;
    private final ComputeWhatIfUseCase computeWhatIfUseCase;
    private final SaveWhatIfPredictionUseCase saveWhatIfPredictionUseCase;
    private final SeasonRepo seasonRepo;
    private final ContestRepo contestRepo;
    private final MatchRepo matchRepo;
    private final TeamRepo teamRepo;
    private final WhatIfPredictionRepo whatIfPredictionRepo;
    private final RoundSupport roundSupport;
    private final CompetitionDefaults competitionDefaults;
    private final ObjectMapper objectMapper;
    private final CurrentUserPublicId currentUserPublicId;
    private final Clock clock;

    @GetMapping
    public String page(
            Principal principal,
            Model model,
            HttpServletResponse response,
            @RequestHeader(value = "HX-Request", required = false) String hxRequest) {
        WebUserDetails userDetails = WebSecurity.resolveUser(principal);
        if (userDetails == null) {
            return bounceToMyTable(response, hxRequest);
        }

        Optional<Season> seasonOpt = seasonRepo.findActiveSeason(competitionDefaults.defaultCompetitionSlug());
        if (seasonOpt.isEmpty()) {
            return bounceToMyTable(response, hxRequest);
        }
        Season season = seasonOpt.get();

        if (!isOpenForWhatIf(season)) {
            return bounceToMyTable(response, hxRequest);
        }

        UUID mainContestId = season.getMainContestId();
        boolean hasMainContestEntry =
                mainContestId != null && contestRepo.existsByUserAndContest(userDetails.getUserId(), mainContestId);

        GetUserPredictionQuery query = GetUserPredictionQuery.forAuthenticatedUser(
                userDetails.getUserId(), season.getId(), hasMainContestEntry, null);

        Either<UseCaseError, UserPredictionViewData> result = getUserPredictionUseCase.execute(query);

        return result.fold(
                error -> bounceToMyTable(response, hxRequest),
                data -> renderPage(data, season, userDetails.getUserId(), model, hxRequest, response));
    }

    private String renderPage(
            UserPredictionViewData data,
            Season season,
            UUID userId,
            Model model,
            String hxRequest,
            HttpServletResponse response) {
        boolean roundOpen = "open".equalsIgnoreCase(data.roundState());
        boolean hasLivePrediction = !data.canCreateEntry();

        if (!data.isCurrentRound() || !hasLivePrediction) {
            return bounceToMyTable(response, hxRequest);
        }

        List<Match> roundMatches = matchRepo.findByRoundIdWithTeams(season.getCurrentRoundId());
        List<WhatIfMatchDto> matches =
                roundMatches.stream().map(WhatIfMatchDto::from).toList();
        List<TeamRankDto> predictions = enrichRankings(data.rankings());

        model.addAttribute("pageTitle", "What-If");
        model.addAttribute("currentRound", data.currentRound());
        model.addAttribute("maxHitPoints", season.getMaxHitPoints());
        model.addAttribute("currentRoundId", season.getCurrentRoundId());
        model.addAttribute("roundOpen", roundOpen);
        model.addAttribute(
                "userId", currentUserPublicId.resolve().map(PublicId::value).orElse("guest"));

        try {
            model.addAttribute("whatIfMatchesJson", objectMapper.writeValueAsString(matches));
            model.addAttribute("predictionsJson", objectMapper.writeValueAsString(predictions));
            model.addAttribute("currentStandingsJson", objectMapper.writeValueAsString(data.standingsMap()));
            model.addAttribute("currentPointsJson", objectMapper.writeValueAsString(data.pointsMap()));
            model.addAttribute("currentGoalDifferenceJson", objectMapper.writeValueAsString(data.goalDifferenceMap()));
            model.addAttribute("savedWhatIfScoresJson", savedScoresJson(userId, season.getCurrentRoundId()));
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize what-if page data", e);
            model.addAttribute("whatIfMatchesJson", "[]");
            model.addAttribute("predictionsJson", "[]");
            model.addAttribute("currentStandingsJson", "{}");
            model.addAttribute("currentPointsJson", "{}");
            model.addAttribute("currentGoalDifferenceJson", "{}");
            model.addAttribute("savedWhatIfScoresJson", "[]");
        }

        if (hxRequest != null && !hxRequest.isBlank()) {
            return "what-if :: whatIfPage";
        }
        return "what-if";
    }

    private String savedScoresJson(UUID userId, UUID roundId) throws JsonProcessingException {
        return objectMapper.writeValueAsString(savedScoresFor(userId, roundId));
    }

    private List<WhatIfScoreDto> savedScoresFor(UUID userId, UUID roundId) {
        if (roundId == null) {
            return List.of();
        }
        return WhatIfScoreDto.listOf(whatIfPredictionRepo
                .findByUserAndRound(userId, roundId)
                .map(WhatIfPrediction::getScores)
                .orElseGet(List::of));
    }

    private boolean isOpenForWhatIf(Season season) {
        Instant now = clock.instant();
        return !season.isCompleted() && !season.isInSetupMode() && (season.isInPlay(now) || season.isPreSeason(now));
    }

    private String bounceToMyTable(HttpServletResponse response, String hxRequest) {
        if (hxRequest != null && !hxRequest.isBlank()) {
            response.setHeader("HX-Redirect", "/my-table");
            return "fragments/error-banner :: banner";
        }
        return "redirect:/my-table";
    }

    private List<TeamRankDto> enrichRankings(List<TeamRank> ranks) {
        if (ranks == null || ranks.isEmpty()) {
            return List.of();
        }
        List<TeamRank> sorted = TeamRank.inPositionOrder(ranks).stream()
                .toList();
        Map<String, Team> teamsByCode =
                teamRepo.findAllByCodes(sorted.stream().map(TeamRank::getCode).collect(Collectors.toSet())).stream()
                        .collect(Collectors.toMap(Team::getCode, Function.identity()));
        return TeamRankDto.listOf(sorted, teamsByCode);
    }

    /**
     * The state the Refresh button resyncs to: the scores this user last applied, plus the round's
     * fixtures and whether it's still open.
     */
    @GetMapping("/saved")
    @ResponseBody
    public Map<String, Object> savedScores(Principal principal, HttpServletResponse response) {
        WebUserDetails userDetails = WebSecurity.resolveUser(principal);
        if (userDetails == null) {
            response.setStatus(401);
            return Map.of("success", false, "message", "Authentication required");
        }

        Round currentRound = seasonRepo
                .findActiveSeason(competitionDefaults.defaultCompetitionSlug())
                .map(roundSupport::resolveCurrentRound)
                .orElse(null);
        UUID roundId = currentRound != null ? currentRound.getId() : null;

        List<WhatIfMatchDto> matches = roundId == null
                ? List.of()
                : matchRepo.findByRoundIdWithTeams(roundId).stream()
                        .map(WhatIfMatchDto::from)
                        .toList();

        return Map.of(
                "success", true,
                "scores", savedScoresFor(userDetails.getUserId(), roundId),
                "matches", matches,
                "roundOpen", roundSupport.resolveStatus(currentRound) == RoundStatus.OPEN);
    }

    @PostMapping("/compute")
    @ResponseBody
    public Map<String, Object> compute(
            @RequestBody WhatIfComputeRequest request, Principal principal, HttpServletResponse response) {
        WebUserDetails userDetails = WebSecurity.resolveUser(principal);
        if (userDetails == null) {
            response.setStatus(401);
            return Map.of("success", false, "message", "Authentication required");
        }

        WhatIfCommand command;
        try {
            command = toCommand(request);
        } catch (IllegalArgumentException e) {
            response.setStatus(400);
            return Map.of("success", false, "message", "Invalid match id");
        }

        Either<WhatIfError, WhatIfResult> result = computeWhatIfUseCase.execute(command);

        return result.fold(
                error -> {
                    response.setStatus(toHttpStatus(error));
                    log.warn("What-if compute failed: {}", error);
                    return Map.of("success", false, "message", errorMessage(error));
                },
                whatIfResult -> {
                    if (whatIfResult.roundOpen()) {
                        persistScores(userDetails.getUserId(), whatIfResult.roundId(), command.scores());
                    }
                    WhatIfComputeResponse body = WhatIfComputeResponse.from(whatIfResult);
                    return Map.of(
                            "success", body.success(),
                            "standingsMap", body.standingsMap(),
                            "pointsMap", body.pointsMap(),
                            "goalDifferenceMap", body.goalDifferenceMap());
                });
    }

    /**
     * Best-effort side-effect: the standings the user is about to see don't depend on the save, so a
     * persistence failure is logged and swallowed rather than failing the compute response. Only
     * called while the round is open — a closed round's saved scores are what the projection is
     * replaying, and must not be overwritten by it.
     */
    private void persistScores(UUID userId, UUID roundId, List<WhatIfScore> scores) {
        try {
            saveWhatIfPredictionUseCase.execute(userId, roundId, scores);
        } catch (RuntimeException e) {
            log.error("Failed to persist what-if scores for user {} round {}", userId, roundId, e);
        }
    }

    private WhatIfCommand toCommand(WhatIfComputeRequest request) {
        List<WhatIfScore> scores = request.scores().stream()
                .map(s -> new WhatIfScore(UUID.fromString(s.matchId()), toGoals(s.homeGoals()), toGoals(s.awayGoals())))
                .toList();
        return new WhatIfCommand(scores);
    }

    private int toGoals(Integer value) {
        return value == null ? -1 : value;
    }

    private int toHttpStatus(WhatIfError error) {
        return switch (error) {
            case WhatIfError.SeasonNotFound __ -> 404;
            case WhatIfError.SeasonNotOpen __ -> 409;
            case WhatIfError.SeasonCompleted __ -> 409;
            case WhatIfError.SeasonInSetupMode __ -> 409;
            case WhatIfError.RoundNotFound __ -> 404;
            case WhatIfError.UnknownMatch __ -> 400;
            case WhatIfError.MissingScores __ -> 400;
            case WhatIfError.InvalidScore __ -> 400;
            case WhatIfError.CalculationFailed __ -> 500;
        };
    }

    private String errorMessage(WhatIfError error) {
        return switch (error) {
            case WhatIfError.SeasonNotFound __ -> "No active season found";
            case WhatIfError.SeasonNotOpen __ -> "Season is not open for what-if right now";
            case WhatIfError.SeasonCompleted __ -> "Season has been completed";
            case WhatIfError.SeasonInSetupMode __ -> "Season is being reconfigured. Please try again shortly.";
            case WhatIfError.RoundNotFound __ -> "Current round not found";
            case WhatIfError.UnknownMatch __ -> "Unknown or non-scoreable match id(s) submitted";
            case WhatIfError.MissingScores e -> "Missing scores for "
                    + e.matchIds().size() + " match(es)";
            case WhatIfError.InvalidScore e -> "Invalid score for match " + e.matchId() + ": " + e.reason();
            case WhatIfError.CalculationFailed e -> e.message();
        };
    }
}
