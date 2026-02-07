package com.ligitabl.api.web.prediction.userpredictions;

import java.security.Principal;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ligitabl.api.config.CompetitionDefaults;
import com.ligitabl.api.shared.Either;
import com.ligitabl.api.shared.errors.UseCaseError;
import com.ligitabl.api.web.shared.dto.response.FixtureDto;
import com.ligitabl.api.web.shared.dto.TeamRankDto;
import com.ligitabl.api.web.shared.error.ErrorMapper;
import com.ligitabl.api.web.shared.error.ErrorViewMapper;
import com.ligitabl.model.auth.Email;
import com.ligitabl.model.auth.PublicId;
import com.ligitabl.model.domain.Match;
import com.ligitabl.model.domain.Season;
import com.ligitabl.model.domain.Team;
import com.ligitabl.model.domain.TeamRank;
import com.ligitabl.model.domain.User;
import com.ligitabl.model.repo.*;

import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Controller
@RequestMapping("/predictions/user")
@RequiredArgsConstructor
@Slf4j
public class UserPredictionsController {
    private final ObjectMapper objectMapper;
    private final GetUserPredictionUseCase getUserPredictionUseCase;
    private final SeasonRepo seasonRepo;
    private final ContestRepo contestRepo;
    private final TeamRepo teamRepo;
    private final UserRepo userRepo;
    private final CompetitionDefaults competitionDefaults;
    private final ErrorViewMapper errorMapper;

    /**
     * GET /predictions/user/me - View current user's prediction.
     *
     * <p>Resolves user from Principal:
     * <ul>
     *   <li>Not logged in → Redirects to /prediction/user/guest</li>
     *   <li>Logged in + has prediction → Returns user's prediction (EDITABLE or READONLY_COOLDOWN)</li>
     *   <li>Logged in + no prediction → Returns fallback as CAN_CREATE_ENTRY</li>
     * </ul>
     */
    @GetMapping("/me")
    public String myPredictions(
            @RequestParam(required = false) Integer round,
            Principal principal,
            Model model,
            HttpServletResponse response,
            @RequestHeader(value = "HX-Request", required = false) String hxRequest) {

        log.info(
                "GET /predictions/user/me - round: {}, user: {}",
                round,
                principal != null ? principal.getName() : "guest");

        // Redirect guests to /guest endpoint - /me implies "my account"
        if (principal == null) {
            String redirect = "redirect:/predictions/user/guest";
            if (round != null) {
                redirect += "?round=" + round;
            }
            return redirect;
        }

        UUID resolvedUserId = resolveAuthenticatedUserId(principal, model, response);
        if (resolvedUserId == null) {
            return "error";
        }

        GetUserPredictionCommand command = buildCommandForMe(resolvedUserId, round);

        Either<UseCaseError, GetUserPredictionUseCase.UserPredictionViewData> result =
                getUserPredictionUseCase.execute(command);

        return result.fold(
                error -> handleError(error, model, response, hxRequest), data -> handleSuccess(data, model, hxRequest));
    }

    /**
     * GET /predictions/user/guest - Explicit guest view.
     *
     * <p>Always returns fallback rankings as READONLY_GUEST, regardless of authentication.</p>
     */
    @GetMapping("/guest")
    public String guestPredictions(
            @RequestParam(required = false) Integer round,
            Model model,
            HttpServletResponse response,
            @RequestHeader(value = "HX-Request", required = false) String hxRequest) {
        log.info("GET /predictions/user/guest - round: {}", round);

        UUID activeSeasonId = getActiveSeason().getId();
        GetUserPredictionCommand command = GetUserPredictionCommand.forGuest(activeSeasonId, round);

        Either<UseCaseError, GetUserPredictionUseCase.UserPredictionViewData> result =
                getUserPredictionUseCase.execute(command);

        return result.fold(
                error -> handleError(error, model, response, hxRequest), data -> handleSuccess(data, model, hxRequest));
    }

    /**
     * GET /predictions/user/{userId} - View specific user's predictions.
     *
     * <p>Resolution logic:
     * <ul>
     *   <li>If userId matches logged-in user → Treat as /me</li>
     *   <li>If userId is valid existing user → Show their prediction (READONLY_VIEWING_OTHER)</li>
     *   <li>If userId doesn't exist → Show fallback (READONLY_USER_NOT_FOUND)</li>
     * </ul>
     */
    @GetMapping("/{userId}")
    public String userPredictions(
            @PathVariable String publicUserId,
            @RequestParam(required = false) Integer round,
            Principal principal,
            Model model,
            HttpServletResponse response,
            @RequestHeader(value = "HX-Request", required = false) String hxRequest) {
        log.info(
                "GET /predictions/user/{} - round: {}, viewer: {}",
                publicUserId,
                round,
                principal != null ? principal.getName() : "guest");

        // Check if viewing own predictions
        if (principal != null && principal.getName().equals(publicUserId)) {
            return myPredictions(round, principal, model, response, hxRequest);
        }

        GetUserPredictionCommand command = buildCommandForUser(publicUserId, round);

        Either<UseCaseError, GetUserPredictionUseCase.UserPredictionViewData> result =
                getUserPredictionUseCase.execute(command);

        return result.fold(
                error -> handleError(error, model, response, hxRequest), data -> handleSuccess(data, model, hxRequest));
    }

    /**
     * Build command for /{userId} endpoint.
     */
    private GetUserPredictionCommand buildCommandForUser(String userIdStr, Integer round) {
        PublicId publicUserId;
        Season activeSeason = getActiveSeason();
        UUID activeSeasonId = activeSeason.getId();
        UUID mainContestId = activeSeason.getMainContestId();

        try {
            publicUserId = PublicId.create(userIdStr);
        } catch (IllegalArgumentException e) {
            // Invalid public id format - treat as user not found
            return GetUserPredictionCommand.forNonExistentUser(activeSeasonId, round);
        }

        var user = userRepo.findByPublicId(publicUserId).orElse(null);
        if (user == null) {
            return GetUserPredictionCommand.forNonExistentUser(activeSeasonId, round);
        }

        UUID targetUserId = user.getId();
        boolean hasMainContestEntry =
                mainContestId != null && contestRepo.existsByUserAndContest(targetUserId, mainContestId);
        String displayName = user.getDisplayName();

        if (hasMainContestEntry) {
            // User exists and has prediction
            return GetUserPredictionCommand.forViewingOtherUser(targetUserId, activeSeasonId, true, displayName, round);
        }

        // User exists but has no prediction yet
        return GetUserPredictionCommand.forViewingOtherUser(targetUserId, activeSeasonId, false, displayName, round);
    }

    /**
     * Build command for /me endpoint based on Principal.
     */
    private GetUserPredictionCommand buildCommandForMe(UUID userId, Integer round) {
        Season season = getActiveSeason();
        UUID activeSeasonId = season.getId();

        if (userId == null) {
            return GetUserPredictionCommand.forGuest(activeSeasonId, round);
        }

        UUID mainContestId = season.getMainContestId();
        boolean hasMainContestEntry =
                mainContestId != null && contestRepo.existsByUserAndContest(userId, mainContestId);
        return GetUserPredictionCommand.forAuthenticatedUser(userId, activeSeasonId, hasMainContestEntry, round);
    }

    private UUID resolveAuthenticatedUserId(Principal principal, Model model, HttpServletResponse response) {
        if (principal == null
                || principal.getName() == null
                || principal.getName().isBlank()) {
            response.setStatus(401);
            model.addAttribute("error", "Unauthenticated");
            return null;
        }

        try {
            Email email = Email.create(principal.getName());
            return userRepo.findByEmail(email).map(User::getId).orElseGet(() -> {
                response.setStatus(401);
                model.addAttribute("error", "User not found");
                return null;
            });
        } catch (IllegalArgumentException e) {
            response.setStatus(400);
            model.addAttribute("error", e.getMessage());
            return null;
        }
    }

    /**
     * Handle use case error.
     */
    private String handleError(UseCaseError error, Model model, HttpServletResponse response, String hxRequest) {
        response.setStatus(mapErrorToStatus(error));
        model.addAttribute("error", errorMapper.toResponse(error));

        if (hxRequest != null && !hxRequest.isBlank()) {
            return "fragments/error-banner :: banner";
        }
        return "error";
    }

    /**
     * Handle successful use case result.
     */
    private String handleSuccess(GetUserPredictionUseCase.UserPredictionViewData data, Model model, String hxRequest) {
        // Convert rankings to DTOs
        List<TeamRankDto> predictions = enrichRankings(data.rankings());

        // Set model attributes for template
        model.addAttribute("pageTitle", getPageTitle(data));
        model.addAttribute("currentRound", data.currentRound());
        model.addAttribute("viewingRound", data.viewingRound());
        model.addAttribute("atRoundNumber", data.atRoundNumber());
        model.addAttribute("isCurrentRound", data.isCurrentRound());
        model.addAttribute("roundState", data.roundState().toLowerCase());
        model.addAttribute("seasonCompleted", data.seasonCompleted());
        model.addAttribute("predictions", predictions);

        // Access mode attributes
        model.addAttribute("accessMode", data.accessMode().name());
        model.addAttribute("canSwap", data.canSwap());
        model.addAttribute("canCreateEntry", data.canCreateEntry());
        model.addAttribute("isReadonly", data.isReadonly());
        model.addAttribute("isGuest", data.isGuest());
        model.addAttribute("isViewingOther", data.isViewingOther());
        model.addAttribute("isUserNotFound", data.isUserNotFound());

        // Message for UI banners
        model.addAttribute("message", data.message());
        model.addAttribute("targetDisplayName", data.targetDisplayName());

        // Swap status for cooldown banners
        if (data.swapCooldown() != null) {
            var cooldown = data.swapCooldown();
            var now = Instant.now();
            model.addAttribute(
                    "swapStatus",
                    new SwapStatusDTO(
                            cooldown.canSwap(now),
                            cooldown.getStatusMessage(now),
                            cooldown.getLastSwapAtFormatted(),
                            cooldown.initialPredictionMade(),
                            cooldown.swapCount()));
        }

        // Round result for historical views
        if (data.hasRoundResult()) {
            var result = data.roundResult();
            model.addAttribute("roundResult", result);
            model.addAttribute("roundResultRankings", result.getRankings());
            model.addAttribute("totalScore", result.getTotalScore());
            model.addAttribute("totalHits", result.getTotalHits());
            model.addAttribute("zeroesCount", result.getZeroesCount());
        }
        model.addAttribute("hasRoundResult", data.hasRoundResult());

        // Source information
        model.addAttribute("source", data.source().name());
        model.addAttribute("sourceLabel", getSourceLabel(data));

        // Serialize data for JavaScript
        try {
            model.addAttribute("fixturesJson", objectMapper.writeValueAsString(buildFixtures(data.matches())));
            model.addAttribute("predictionsJson", objectMapper.writeValueAsString(predictions));
            model.addAttribute("currentStandingsJson", objectMapper.writeValueAsString(data.standingsMap()));
            model.addAttribute("currentPointsJson", objectMapper.writeValueAsString(data.pointsMap()));
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize data", e);
            model.addAttribute("fixturesJson", "{}");
            model.addAttribute("predictionsJson", "[]");
            model.addAttribute("currentStandingsJson", "{}");
            model.addAttribute("currentPointsJson", "{}");
        }

        // Return appropriate view
        if (hxRequest != null && !hxRequest.isBlank()) {
            return "predictions :: predictionPage";
        }
        return "predictions";
    }

    private Season getActiveSeason() {
        return seasonRepo
                .findMostRecentSeason(competitionDefaults.defaultCompetitionSlug())
                .orElseThrow(() -> new IllegalStateException("No active season available"));
    }

    private List<TeamRankDto> enrichRankings(List<TeamRank> ranks) {
        if (ranks == null || ranks.isEmpty()) {
            return List.of();
        }

        List<TeamRank> sortedRanks = ranks.stream()
                .sorted(Comparator.comparingInt(TeamRank::getPosition))
                .toList();

        Map<String, Team> teamsByCode =
                teamRepo
                        .findAllByCodes(
                                sortedRanks.stream().map(TeamRank::getCode).collect(Collectors.toSet()))
                        .stream()
                        .collect(Collectors.toMap(Team::getCode, Function.identity()));
        return TeamRankDto.listOf(sortedRanks, teamsByCode);
    }

    private Map<String, List<FixtureDto>> buildFixtures(Map<String, List<Match>> matchesByTeam) {
        if (matchesByTeam == null || matchesByTeam.isEmpty()) {
            return Map.of();
        }

        return matchesByTeam.entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey, entry -> entry.getValue().stream()
                        .map(match -> toFixture(entry.getKey(), match))
                        .filter(Objects::nonNull)
                        .toList()));
    }

    private FixtureDto toFixture(String teamCode, Match match) {
        if (match == null || !match.hasTeamsLoaded()) {
            return null;
        }

        Team home = match.getHomeTeam();
        Team away = match.getAwayTeam();
        if (home == null || away == null) {
            return null;
        }

        boolean isHome = teamCode.equals(home.getCode());
        String opponent = isHome ? away.getCode() : home.getCode();
        return new FixtureDto(opponent, isHome);
    }

    /**
     * Get page title based on access mode.
     */
    private String getPageTitle(GetUserPredictionUseCase.UserPredictionViewData data) {
        return switch (data.accessMode()) {
            case EDITABLE, READONLY_COOLDOWN -> "My Predictions";
            case CAN_CREATE_ENTRY -> "Create Prediction";
            case READONLY_GUEST -> "Predictions";
            case READONLY_VIEWING_OTHER -> data.targetDisplayName() != null
                    ? data.targetDisplayName() + "'s Predictions"
                    : "User Predictions";
            case READONLY_USER_NOT_FOUND -> "User Not Found";
        };
    }

    /**
     * Get source label for UI display.
     */
    private String getSourceLabel(GetUserPredictionUseCase.UserPredictionViewData data) {
        return switch (data.source()) {
            case USER_PREDICTION -> "Your Prediction";
            case ROUND_STANDINGS -> "Current Standings";
            case SEASON_BASELINE -> "Season Baseline";
        };
    }

    /**
     * Map use case error to HTTP status code.
     */
    private int mapErrorToStatus(UseCaseError error) {
        return ErrorMapper.toHttpStatus(error);
    }

    /**
     * DTO for swap status information displayed in templates.
     */
    public record SwapStatusDTO(
            boolean canSwap, String message, String lastSwapAt, boolean initialPredictionMade, int swapCount) {
        /**
         * Check if this is the first swap bonus (can swap without cooldown).
         */
        public boolean isFirstSwapBonus() {
            return initialPredictionMade && swapCount == 0 && canSwap;
        }
    }
}
