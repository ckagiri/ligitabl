package com.ligitabl.api.web.predictions.userpredictions;

import java.security.Principal;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.ToIntFunction;
import java.util.stream.Collectors;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ligitabl.api.auth.CurrentUserPublicId;
import com.ligitabl.api.auth.oauth2.LigitablOAuth2User;
import com.ligitabl.api.auth.security.WebUserDetails;
import com.ligitabl.api.config.CompetitionDefaults;
import com.ligitabl.api.rest.standings.FormService;
import com.ligitabl.api.shared.Either;
import com.ligitabl.api.shared.errors.UseCaseError;
import com.ligitabl.api.web.shared.dto.FixtureDto;
import com.ligitabl.api.web.shared.dto.ResultTeamRankDto;
import com.ligitabl.api.web.shared.dto.TeamRankDto;
import com.ligitabl.api.web.shared.error.ErrorMapper;
import com.ligitabl.api.web.shared.error.ErrorViewMapper;
import com.ligitabl.model.auth.Email;
import com.ligitabl.model.auth.PublicId;
import com.ligitabl.model.domain.Match;
import com.ligitabl.model.domain.MatchResult;
import com.ligitabl.model.domain.MatchStatus;
import com.ligitabl.model.domain.ResultTeamRank;
import com.ligitabl.model.domain.Season;
import com.ligitabl.model.domain.SwapChange;
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
    private final CurrentUserPublicId currentUserPublicId;
    private final GetUserPredictionUseCase getUserPredictionUseCase;
    private final SeasonRepo seasonRepo;
    private final ContestRepo contestRepo;
    private final TeamRepo teamRepo;
    private final UserRepo userRepo;
    private final CompetitionDefaults competitionDefaults;
    private final ErrorViewMapper errorMapper;
    private final FormService formService;

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

        var seasonOpt = getActiveSeason();
        if (seasonOpt.isEmpty()) {
            return handleNoActiveSeason(model, response, hxRequest);
        }

        Season season = seasonOpt.get();

        GetUserPredictionQuery query = buildQueryForMe(resolvedUserId, round, season);

        Either<UseCaseError, UserPredictionViewData> result = getUserPredictionUseCase.execute(query);

        return result.fold(
                error -> handleError(error, model, response, hxRequest),
                data -> handleSuccess(data, model, hxRequest, season));
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

        var seasonOpt = getActiveSeason();
        if (seasonOpt.isEmpty()) {
            return handleNoActiveSeason(model, response, hxRequest);
        }

        Season season = seasonOpt.get();
        UUID activeSeasonId = season.getId();
        GetUserPredictionQuery query = GetUserPredictionQuery.forGuest(activeSeasonId, round);

        Either<UseCaseError, UserPredictionViewData> result = getUserPredictionUseCase.execute(query);

        return result.fold(
                error -> handleError(error, model, response, hxRequest),
                data -> handleSuccess(data, model, hxRequest, season));
    }

    /**
     * Build query for /me endpoint based on Principal.
     */
    private GetUserPredictionQuery buildQueryForMe(UUID userId, Integer round, Season season) {
        UUID activeSeasonId = season.getId();

        if (userId == null) {
            return GetUserPredictionQuery.forGuest(activeSeasonId, round);
        }

        UUID mainContestId = season.getMainContestId();
        boolean hasMainContestEntry =
                mainContestId != null && contestRepo.existsByUserAndContest(userId, mainContestId);
        return GetUserPredictionQuery.forAuthenticatedUser(userId, activeSeasonId, hasMainContestEntry, round);
    }

    private UUID resolveAuthenticatedUserId(Principal principal, Model model, HttpServletResponse response) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null) {
            Object authPrincipal = authentication.getPrincipal();

            if (authPrincipal instanceof WebUserDetails webUserDetails) {
                return webUserDetails.getUserId();
            }

            if (authPrincipal instanceof LigitablOAuth2User ligitablOAuth2User) {
                return ligitablOAuth2User.getUser().getId();
            }

            if (authPrincipal instanceof OAuth2User oauth2User) {
                Object emailAttr = oauth2User.getAttributes().get("email");
                if (emailAttr != null) {
                    try {
                        Email email = Email.create(String.valueOf(emailAttr));
                        return userRepo.findByEmail(email).map(User::getId).orElseGet(() -> {
                            response.setStatus(401);
                            model.addAttribute("error", "User not found");
                            return null;
                        });
                    } catch (IllegalArgumentException ignored) {
                        // fall through to generic principal-name fallback below
                    }
                }
            }
        }

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

    private String handleNoActiveSeason(Model model, HttpServletResponse response, String hxRequest) {
        response.setStatus(404);
        model.addAttribute("error", "No active season available");

        if (hxRequest != null && !hxRequest.isBlank()) {
            return "fragments/error-banner :: banner";
        }
        return "error";
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
    private String handleSuccess(UserPredictionViewData data, Model model, String hxRequest, Season season) {
        // Convert rankings to DTOs
        List<TeamRankDto> predictions = enrichRankings(data.rankings());

        // Current authenticated user id for client-side user-scoped storage keys.
        model.addAttribute(
                "userId", currentUserPublicId.resolve().map(PublicId::value).orElse("guest"));
        model.addAttribute("currentRoundId", season.getCurrentRoundId());

        // Set model attributes for template
        model.addAttribute("pageTitle", getPageTitle(data));
        model.addAttribute("currentRound", data.currentRound());
        model.addAttribute("lastRound", data.lastRound());
        model.addAttribute("viewingRound", data.viewingRound());
        model.addAttribute("atRoundNumber", data.atRoundNumber());
        model.addAttribute("isCurrentRound", data.isCurrentRound());
        model.addAttribute("roundState", data.roundState().toLowerCase());
        model.addAttribute("seasonCompleted", data.seasonCompleted());
        model.addAttribute("seasonInSetupMode", season.isInSetupMode());
        model.addAttribute("predictions", predictions);

        // Access mode attributes
        model.addAttribute("accessMode", data.accessMode().name());
        model.addAttribute("canSwap", data.canSwap());
        model.addAttribute("canCreateEntry", data.canCreateEntry());
        model.addAttribute("isReadonly", data.isReadonly());
        model.addAttribute("isGuest", data.isGuest());
        model.addAttribute("isUserNotFound", data.isUserNotFound());

        // Swap status for cooldown banners
        if (data.swapCooldown() != null) {
            var cooldown = data.swapCooldown();
            var now = Instant.now();
            boolean firstSwapBonus = cooldown.initialPredictionMade() && cooldown.lastSwapAt() == null;
            model.addAttribute(
                    "swapStatus",
                    new SwapStatusDTO(
                            cooldown.canSwap(now),
                            cooldown.getStatusMessage(now),
                            cooldown.getLastSwapAtFormatted(),
                            cooldown.initialPredictionMade(),
                            firstSwapBonus,
                            cooldown.openingRoundAvailable()));
            model.addAttribute("isOpeningRound", cooldown.openingRoundAvailable());
        } else {
            model.addAttribute("isOpeningRound", false);
        }

        // canInteract: can rearrange the table regardless of cooldown (false for read-only views)
        boolean canInteractWithTable = data.canSwap()
                || (data.swapCooldown() != null && data.swapCooldown().initialPredictionMade());
        model.addAttribute("canInteract", canInteractWithTable);

        // Swap history (own predictions only)
        if (data.roundSwapHistory() != null && !data.roundSwapHistory().isEmpty()) {
            model.addAttribute("swapHistory", formatSwapHistory(data.roundSwapHistory()));
        }

        // Round result for historical views
        if (data.hasRoundResult()) {
            var result = data.roundResult();
            model.addAttribute("roundResult", result);
            model.addAttribute("roundResultRankings", enrichResultRankings(result.getRankings()));
            model.addAttribute("totalScore", result.getTotalScore());
            model.addAttribute("totalHits", result.getTotalHits());
            model.addAttribute("zeroesCount", result.getZeroesCount());
            model.addAttribute("seasonBestScore", data.seasonBestScore());
            model.addAttribute("sprintBestScore", data.sprintBestScore());
            model.addAttribute("sprintLabel", data.sprintLabel());
        }
        model.addAttribute("hasRoundResult", data.hasRoundResult());

        // Season phase state — used for off-season/pre-season UI branches
        model.addAttribute("isPreSeason", season.isPreSeason());
        model.addAttribute("isOffSeason", season.isOffSeason());
        model.addAttribute("isPredictionsOpen", season.isPredictionsOpen());
        if (season.getPreSeasonOpensAt() != null && !season.isPreSeasonOpen()) {
            long days = ChronoUnit.DAYS.between(OffsetDateTime.now(), season.getPreSeasonOpensAt());
            model.addAttribute("daysToPreSeason", Math.max(0, days));
        }
        if (season.isPreSeason() && season.getPredictionsOpenAt() != null) {
            long days = ChronoUnit.DAYS.between(OffsetDateTime.now(), season.getPredictionsOpenAt());
            model.addAttribute("daysToPredictions", Math.max(0, days));
            model.addAttribute("predictionsOpenAt", season.getPredictionsOpenAt());
        }

        // Source information
        model.addAttribute("source", data.source().name());
        model.addAttribute("sourceLabel", getSourceLabel(data));

        // Serialize data for JavaScript
        try {
            var formMap = formService.buildFormMap(season.getId(), data.viewingRound());
            model.addAttribute("fixturesJson", objectMapper.writeValueAsString(buildFixtures(data.matches())));
            model.addAttribute("predictionsJson", objectMapper.writeValueAsString(predictions));
            model.addAttribute("currentStandingsJson", objectMapper.writeValueAsString(data.standingsMap()));
            model.addAttribute("currentPointsJson", objectMapper.writeValueAsString(data.pointsMap()));
            model.addAttribute("currentGoalDifferenceJson", objectMapper.writeValueAsString(data.goalDifferenceMap()));
            model.addAttribute("formJson", objectMapper.writeValueAsString(formMap));
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize data", e);
            model.addAttribute("fixturesJson", "{}");
            model.addAttribute("predictionsJson", "[]");
            model.addAttribute("currentStandingsJson", "{}");
            model.addAttribute("currentPointsJson", "{}");
            model.addAttribute("currentGoalDifferenceJson", "{}");
            model.addAttribute("formJson", "{}");
        }

        // Return appropriate view
        if (hxRequest != null && !hxRequest.isBlank()) {
            return "predictions :: predictionPage";
        }
        return "predictions";
    }

    private Optional<Season> getActiveSeason() {
        return seasonRepo.findActiveSeason(competitionDefaults.defaultCompetitionSlug());
    }

    private static <T> List<T> sortByPosition(List<T> items, ToIntFunction<T> positionFn) {
        if (items == null || items.isEmpty()) {
            return List.of();
        }

        return items.stream().sorted(Comparator.comparingInt(positionFn)).toList();
    }

    private List<TeamRankDto> enrichRankings(List<TeamRank> ranks) {
        if (ranks == null || ranks.isEmpty()) {
            return List.of();
        }

        List<TeamRank> sortedRanks = sortByPosition(ranks, TeamRank::getPosition);

        Map<String, Team> teamsByCode =
                teamRepo
                        .findAllByCodes(
                                sortedRanks.stream().map(TeamRank::getCode).collect(Collectors.toSet()))
                        .stream()
                        .collect(Collectors.toMap(Team::getCode, Function.identity()));
        return TeamRankDto.listOf(sortedRanks, teamsByCode);
    }

    private List<ResultTeamRankDto> enrichResultRankings(List<ResultTeamRank> resultRanks) {
        if (resultRanks == null || resultRanks.isEmpty()) {
            return List.of();
        }

        List<ResultTeamRank> sortedRanks =
                sortByPosition(resultRanks, r -> r.getRanking().getPosition());

        Map<String, Team> teamsByCode = teamRepo
                .findAllByCodes(
                        sortedRanks.stream().map(r -> r.getRanking().getCode()).collect(Collectors.toSet()))
                .stream()
                .collect(Collectors.toMap(Team::getCode, Function.identity()));

        return ResultTeamRankDto.listOf(sortedRanks, teamsByCode);
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

    FixtureDto toFixture(String teamCode, Match match) {
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
        return new FixtureDto(
                opponent, isHome, normalizeFixtureStatus(match.getStatus()), resolveFixtureResult(match, isHome));
    }

    static String normalizeFixtureStatus(MatchStatus status) {
        if (status == null) {
            return MatchStatus.SCHEDULED.name();
        }

        return switch (status) {
            case LIVE, SUSPENDED -> MatchStatus.LIVE.name();
            case FINISHED -> MatchStatus.FINISHED.name();
            case POSTPONED -> MatchStatus.POSTPONED.name();
            case SCHEDULED, CANCELLED -> MatchStatus.SCHEDULED.name();
        };
    }

    static String resolveFixtureResult(Match match, boolean isHome) {
        if (match == null || match.getStatus() != MatchStatus.FINISHED) {
            return null;
        }

        return match.result().map(result -> toPerspectiveResult(result, isHome)).orElse(null);
    }

    private static String toPerspectiveResult(MatchResult result, boolean isHome) {
        if (result.isDraw()) {
            return "DRAW";
        }

        boolean teamWon = isHome ? result.isHomeWin() : result.isAwayWin();
        return teamWon ? "WIN" : "LOSS";
    }

    /**
     * Get page title based on access mode.
     */
    private String getPageTitle(UserPredictionViewData data) {
        return switch (data.accessMode()) {
            case EDITABLE, READONLY_COOLDOWN -> "My Table";
            case CAN_CREATE_ENTRY -> "Create Prediction";
            case READONLY_GUEST -> "My Table";
            case READONLY_USER_NOT_FOUND -> "User Not Found";
        };
    }

    /**
     * Get source label for UI display.
     */
    private String getSourceLabel(UserPredictionViewData data) {
        return switch (data.source()) {
            case USER_PREDICTION -> "Your Table";
            case CURRENT_ROUND_STANDINGS -> "Current Gameweek Standings";
            case PREVIOUS_ROUND_STANDINGS -> "Pre-Previous Gameweek Baseline";
            case SEASON_BASELINE -> "Last Season Baseline";
        };
    }

    /**
     * Map use case error to HTTP status code.
     */
    private int mapErrorToStatus(UseCaseError error) {
        return ErrorMapper.toHttpStatus(error);
    }

    private List<SwapHistoryEntryDTO> formatSwapHistory(List<SwapChange> changes) {
        return changes.stream()
                .sorted(Comparator.comparing(SwapChange::timestamp))
                .map(swap -> {
                    String[] partsA = swap.teamA().split(":");
                    String[] posA = partsA[1].split("\u2192"); // →
                    String[] partsB = swap.teamB().split(":");
                    String[] posB = partsB[1].split("\u2192"); // →
                    return new SwapHistoryEntryDTO(
                            partsA[0],
                            Integer.parseInt(posA[0]),
                            Integer.parseInt(posA[1]),
                            partsB[0],
                            Integer.parseInt(posB[0]),
                            Integer.parseInt(posB[1]),
                            swap.timestamp().toString()); // ISO 8601 UTC — formatted client-side to user's locale
                })
                .toList();
    }

    /**
     * DTO for swap status information displayed in templates.
     */
    public record SwapStatusDTO(
            boolean canSwap,
            String message,
            String lastSwapAt,
            boolean initialPredictionMade,
            boolean firstSwapBonus,
            boolean openingRoundAvailable) {}

    /**
     * DTO for a single swap change entry displayed in the swap history section.
     */
    public record SwapHistoryEntryDTO(
            String teamACode,
            int teamAFrom,
            int teamATo,
            String teamBCode,
            int teamBFrom,
            int teamBTo,
            String formattedTime) {}
}
