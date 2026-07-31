package com.ligitabl.api.web.predictions.userpredictions;

import java.security.Principal;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
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
import com.ligitabl.api.auth.impersonation.CurrentUserFacade;
import com.ligitabl.api.auth.impersonation.UserSummary;
import com.ligitabl.api.auth.oauth2.LigitablOAuth2User;
import com.ligitabl.api.auth.security.WebUserDetails;
import com.ligitabl.api.config.CompetitionDefaults;
import com.ligitabl.api.rest.standings.FormService;
import com.ligitabl.api.shared.Either;
import com.ligitabl.api.shared.errors.UseCaseError;
import com.ligitabl.api.web.shared.dto.ResultTeamRankDto;
import com.ligitabl.api.web.shared.dto.TeamRankDto;
import com.ligitabl.api.web.shared.error.ErrorMapper;
import com.ligitabl.api.web.shared.error.ErrorViewMapper;
import com.ligitabl.api.web.shared.fixtures.FixtureJsonMapper;
import com.ligitabl.api.web.shared.season.SeasonPhase;
import com.ligitabl.api.web.shared.season.SeasonPredictionSupport;
import com.ligitabl.model.auth.Email;
import com.ligitabl.model.auth.PublicId;
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
    private final CurrentUserFacade currentUserFacade;
    private final GetUserPredictionUseCase getUserPredictionUseCase;
    private final SeasonRepo seasonRepo;
    private final ContestRepo contestRepo;
    private final TeamRepo teamRepo;
    private final UserRepo userRepo;
    private final CompetitionDefaults competitionDefaults;
    private final ErrorViewMapper errorMapper;
    private final FormService formService;
    private final FixtureJsonMapper fixtureJsonMapper;
    private final RoundRepo roundRepo;
    private final WhatIfRecapBuilder whatIfRecapBuilder;
    private final SeasonPredictionSupport seasonPredictionSupport;

    /**
     * GET /predictions/user/me - View current user's prediction.
     *
     * <p>Resolves user from Principal:
     * <ul>
     *   <li>Not logged in → Redirects to /prediction/user/guest</li>
     *   <li>Logged in + has prediction → Returns user's prediction (EDITABLE or READONLY)</li>
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
                data -> handleSuccess(data, model, hxRequest, season, resolvedUserId));
    }

    /**
     * GET /predictions/user/guest - Explicit guest view.
     *
     * <p>Always returns fallback rankings as READONLY, regardless of authentication.</p>
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
                data -> handleSuccess(data, model, hxRequest, season, null));
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
        // Data flows follow the effective user while an admin is impersonating
        if (currentUserFacade.isImpersonating()) {
            UUID effectiveId =
                    currentUserFacade.getEffectiveUser().map(UserSummary::id).orElse(null);
            if (effectiveId != null) {
                return effectiveId;
            }
        }

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

    private void addWhatIfRecap(Model model, Season season, int viewingRound, UUID userId) {
        if (userId == null) {
            return;
        }
        UUID roundId = roundRepo
                .findBySeasonIdAndPosition(season.getId(), viewingRound)
                .map(round -> round.getId())
                .orElse(null);

        whatIfRecapBuilder.build(userId, roundId).ifPresent(recap -> {
            model.addAttribute("whatIfRecap", recap);
            try {
                model.addAttribute(
                        "whatIfRecapJson",
                        objectMapper.writeValueAsString(Map.of(
                                "round", viewingRound,
                                "played", recap.played(),
                                "all", recap.all(),
                                "wins", recap.wins(),
                                "draws", recap.draws(),
                                "losses", recap.losses())));
            } catch (JsonProcessingException e) {
                log.error("Failed to serialize what-if recap", e);
                model.addAttribute("whatIfRecapJson", "{}");
            }
        });
    }

    /**
     * Share text/link for the "Share your prediction" card, the same data the profile settings page
     * uses. Hidden for guests and for anyone without a pre-season registration or in-play prediction
     * — {@code buildShareData} decides that, so the two pages can't disagree about what's shareable.
     *
     * <p>Also hidden on historical views: sharing is about your live table, and {@code buildShareData}
     * always describes the <i>current</i> round, so on a past round it would offer a share that
     * doesn't match what's on screen.
     */
    private void addShareData(Model model, UUID userId, boolean isHistoricalView) {
        var shareData = userId == null || isHistoricalView
                ? SeasonPredictionSupport.ShareData.hidden()
                : userRepo.findById(userId)
                        .map(user -> seasonPredictionSupport.buildShareData(
                                user, competitionDefaults.defaultCompetitionSlug()))
                        .orElseGet(SeasonPredictionSupport.ShareData::hidden);

        model.addAttribute("showShareSection", shareData.visible());
        model.addAttribute("shareUrl", shareData.shareUrl());
        model.addAttribute("shareText", shareData.shareText());
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
    private String handleSuccess(
            UserPredictionViewData data, Model model, String hxRequest, Season season, UUID userId) {
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
        model.addAttribute("joinedAtRound", data.joinedAtRound());
        model.addAttribute("isCurrentRound", data.isCurrentRound());
        String roundStateValue = data.roundState().toLowerCase();
        model.addAttribute("roundState", roundStateValue);
        model.addAttribute("seasonCompleted", data.seasonCompleted());
        model.addAttribute("seasonInSetupMode", season.isInSetupMode());
        model.addAttribute("predictions", predictions);

        // Round-level derived flags, computed once here rather than re-derived per template/fragment
        boolean isOpenRoundState = roundStateValue.equals("open");
        boolean isLockedRoundState = roundStateValue.equals("locked");
        boolean isCompletedRoundState = roundStateValue.equals("completed");
        boolean isFinalizedRoundState = roundStateValue.equals("finalized");
        boolean isFutureRoundPrediction = data.atRoundNumber() != null && data.atRoundNumber() > data.currentRound();
        boolean isRoundOpenForPrediction = isOpenRoundState || isFutureRoundPrediction;
        boolean isLastRound = data.currentRound() == data.lastRound();
        boolean notLastRound = !isLastRound;
        boolean isCurrentRoundLast = data.isCurrentRound() && isLastRound;
        boolean isLastRoundOpen = isLastRound && isOpenRoundState;
        boolean isHistoricalView = !data.isCurrentRound() || (data.isCurrentRound() && data.seasonCompleted());
        boolean notLastRoundClosed = !isLastRound || isLastRoundOpen;
        // Games have started (locked), finished (completed), or just been finalized — but not yet advanced.
        boolean isRoundLockedOrBeyond = isLockedRoundState || isCompletedRoundState || isFinalizedRoundState;
        model.addAttribute("isFutureRoundPrediction", isFutureRoundPrediction);
        model.addAttribute("isRoundOpenForPrediction", isRoundOpenForPrediction);
        model.addAttribute("isLastRound", isLastRound);
        model.addAttribute("notLastRound", notLastRound);
        model.addAttribute("isCurrentRoundLast", isCurrentRoundLast);
        model.addAttribute("isLastRoundOpen", isLastRoundOpen);
        model.addAttribute("isHistoricalView", isHistoricalView);
        model.addAttribute("isRoundLockedOrBeyond", isRoundLockedOrBeyond);

        // Access mode attributes
        model.addAttribute("accessMode", data.accessMode().name());
        model.addAttribute("canSwap", data.canSwap());
        model.addAttribute("canCreateEntry", data.canCreateEntry());
        model.addAttribute("isReadonly", data.isReadonly());
        model.addAttribute("isGuest", data.isGuest());

        // Swap status for cooldown banners — SwapStatusDTO.none() when there's no cooldown at all
        // (guest, no-prediction-fallback, historical view), so templates never need swapStatus != null.
        SwapStatusDTO swapStatus = SwapStatusDTO.none();
        boolean isOpeningRound = false;
        if (data.swapCooldown() != null) {
            var cooldown = data.swapCooldown();
            var now = Instant.now();
            boolean firstSwapBonus = cooldown.initialPredictionMade() && cooldown.lastSwapAt() == null;
            swapStatus = new SwapStatusDTO(
                    cooldown.canSwap(now),
                    cooldown.getStatusMessage(now),
                    cooldown.getLastSwapAtFormatted(),
                    cooldown.initialPredictionMade(),
                    firstSwapBonus,
                    cooldown.openingRoundAvailable());
            isOpeningRound = cooldown.openingRoundAvailable();
        }
        model.addAttribute("swapStatus", swapStatus);
        model.addAttribute("isOpeningRound", isOpeningRound);

        // canInteractWithTable: can rearrange the table regardless of cooldown (false for read-only views)
        boolean canInteractWithTable = data.canSwap()
                || (data.swapCooldown() != null && data.swapCooldown().initialPredictionMade());

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
            addWhatIfRecap(model, season, data.viewingRound(), userId);
        }
        model.addAttribute("hasRoundResult", data.hasRoundResult());
        addShareData(model, userId, isHistoricalView);

        // Season phase state — used for off-season/pre-season UI branches
        SeasonPhase phase = SeasonPhase.resolve(season);
        model.addAttribute("isPreSeason", phase.isPreSeason());
        model.addAttribute("isOffSeason", phase.isOffSeason());
        model.addAttribute("isInPlay", phase.isInPlay());
        model.addAttribute("isInactive", phase.isInactive());
        boolean seasonAllowsUpdate = phase.isInPlay() || (phase.isPreSeason() && !data.hasPreSeasonRegistration());
        model.addAttribute("seasonAllowsUpdate", seasonAllowsUpdate);

        // Combined editing gates, computed once rather than re-derived per template/fragment.
        boolean seasonEditingAllowed = !season.isInSetupMode() && seasonAllowsUpdate;
        boolean canInteractEffective =
                canInteractWithTable && !isHistoricalView && seasonEditingAllowed && notLastRoundClosed;
        boolean canSwapEditable = (data.canSwap() || isOpeningRound)
                && !isHistoricalView
                && seasonEditingAllowed
                && isRoundOpenForPrediction;
        boolean isInitialPredictionEditable = data.canCreateEntry() && seasonEditingAllowed && isRoundOpenForPrediction;
        model.addAttribute("seasonEditingAllowed", seasonEditingAllowed);
        model.addAttribute("canInteractEffective", canInteractEffective);
        model.addAttribute("canSwapEditable", canSwapEditable);
        model.addAttribute("isInitialPredictionEditable", isInitialPredictionEditable);
        // Still-unmerged pre-season "easter egg" registration (atRoundNumber == 0) — treated like an
        // initial prediction (5-swap allowance, no cooldown) everywhere isInitialPrediction is checked,
        // without changing accessMode itself.
        model.addAttribute("isPreSeasonRegistration", data.hasPreSeasonRegistration());
        if (phase.daysToPreSeason() != null) {
            model.addAttribute("daysToPreSeason", phase.daysToPreSeason());
        }
        if (phase.preSeasonAboutToStart()) {
            model.addAttribute("preSeasonAboutToStart", true);
        }
        if (phase.daysToPredictions() != null) {
            model.addAttribute("daysToPredictions", phase.daysToPredictions());
        }
        if (phase.predictionsAboutToStart()) {
            model.addAttribute("predictionsAboutToStart", true);
        }
        if (phase.predictionsOpenAt() != null) {
            model.addAttribute("predictionsOpenAt", phase.predictionsOpenAt());
        }

        // Source information
        model.addAttribute("source", data.source().name());
        model.addAttribute("sourceLabel", getSourceLabel(data));

        // Serialize data for JavaScript
        try {
            var formMap = formService.buildFormMap(season.getId(), data.viewingRound());
            model.addAttribute(
                    "fixturesJson", objectMapper.writeValueAsString(fixtureJsonMapper.buildFixtures(data.matches())));
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

    /**
     * Get page title based on access mode.
     */
    private String getPageTitle(UserPredictionViewData data) {
        return switch (data.accessMode()) {
            case EDITABLE, READONLY -> "My Table";
            case CAN_CREATE_ENTRY -> "Create Prediction";
        };
    }

    /**
     * Get source label for UI display.
     */
    private String getSourceLabel(UserPredictionViewData data) {
        return switch (data.source()) {
            case USER_PREDICTION -> "Your Table";
            case CURRENT_ROUND_STANDINGS -> "Current Gameweek Standings";
            case PREVIOUS_ROUND_STANDINGS -> "Previous Gameweek Baseline";
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
            boolean openingRoundAvailable) {

        /**
         * Null object for views with no swap cooldown (guest, no-prediction-fallback, historical
         * views) — lets templates read {@code swapStatus.firstSwapBonus}/{@code
         * .openingRoundAvailable} directly instead of guarding every read with {@code swapStatus !=
         * null}.
         */
        public static SwapStatusDTO none() {
            return new SwapStatusDTO(false, "", "Never", false, false, false);
        }
    }

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
