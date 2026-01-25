package com.ligitabl.api.presentation.web;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ligitabl.api.auth.CurrentUserId;
import com.ligitabl.api.config.CompetitionDefaults;
import com.ligitabl.api.usecases.prediction.getprediction.GetPredictionUseCase;
import com.ligitabl.api.usecases.prediction.getprediction.PredictionRankDto;
import com.ligitabl.model.auth.PublicId;
import com.ligitabl.model.domain.Match;
import com.ligitabl.model.domain.MatchStatus;
import com.ligitabl.model.domain.Round;
import com.ligitabl.model.domain.RoundResult;
import com.ligitabl.model.domain.RoundStatus;
import com.ligitabl.model.domain.ResultTeamRank;
import com.ligitabl.model.domain.Season;
import com.ligitabl.model.domain.SeasonPrediction;
import com.ligitabl.model.domain.Team;
import com.ligitabl.model.domain.TeamRank;
import com.ligitabl.model.repo.MatchRepo;
import com.ligitabl.model.repo.RoundRepo;
import com.ligitabl.model.repo.RoundResultRepo;
import com.ligitabl.model.repo.RoundSubmissionRepo;
import com.ligitabl.model.repo.SeasonPredictionRepo;
import com.ligitabl.model.repo.SeasonRepo;
import com.ligitabl.model.repo.StandingsRepo;
import com.ligitabl.model.repo.TeamRepo;
import com.ligitabl.model.repo.UserRepo;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Controller
@RequestMapping("/predictions/user")
@RequiredArgsConstructor
@Slf4j
public class UserPredictionsController2 {
    private final CompetitionDefaults competitionDefaults;
    private final SeasonRepo seasonRepo;
    private final RoundRepo roundRepo;
    private final MatchRepo matchRepo;
    private final StandingsRepo standingsRepo;
    private final SeasonPredictionRepo seasonPredictionRepo;
    private final RoundSubmissionRepo roundSubmissionRepo;
    private final RoundResultRepo roundResultRepo;
    private final TeamRepo teamRepo;
    private final UserRepo userRepo;
    private final CurrentUserId currentUserId;
    private final GetPredictionUseCase getPredictionUseCase;
    private final ObjectMapper objectMapper;

    @GetMapping("/me")
    public String getMyPredictions(
            @RequestParam(required = false) Integer round,
            @AuthenticationPrincipal UserDetails userDetails,
            @RequestHeader(value = "HX-Request", required = false) String hxRequest,
            Model model) {
        if (userDetails == null) {
            return redirectToGuest(round);
        }

        log.info("Get /prediction/user/me - round: {}, user {}", round, userDetails.getUsername());

        return buildViewForUser(currentUserId.require(), userDetails.getUsername(), round, hxRequest, model,
                AccessMode.ME);
    }

    @GetMapping("/{userId}")
    public String getUserPrediction(
            @PathVariable String userId,
            @RequestParam(required = false) Integer round,
            @RequestHeader(value = "HX-Request", required = false) String hxRequest,
            Model model) {
        log.info("Web prediction (/{}) request", userId);
        UUID targetUserId = resolveTargetUserId(userId, model);
        if (targetUserId == null) {
            populateUserNotFound(model, round);
            return resolveView(hxRequest);
        }

        return buildViewForUser(targetUserId, null, round, hxRequest, model, AccessMode.VIEW_OTHER);
    }

    @GetMapping("/guest")
    public String getGuestPrediction(
            @RequestParam(required = false) Integer round,
            @RequestHeader(value = "HX-Request", required = false) String hxRequest,
            Model model) {
        log.info("Web prediction (/guest) request");
        return buildViewForGuest(round, hxRequest, model);
    }

    private String buildViewForGuest(Integer round, String hxRequest, Model model) {
        Season season = getActiveSeason();
        Round currentRound = getCurrentRound(season);
        int viewingRound = resolveViewingRound(round, currentRound.getPosition());
        boolean isCurrentRound = viewingRound == currentRound.getPosition();

        List<TeamRank> rankings = getFallbackRankings(season, currentRound.getPosition());
        List<PredictionRankDto> enriched = enrichRankings(rankings);

        populateBaseModel(model, "Predictions", viewingRound, currentRound.getPosition(), isCurrentRound);
        model.addAttribute("message", isCurrentRound
                ? "Log in to create your prediction"
                : "Viewing Gameweek " + viewingRound + " results");
        setRoundState(model, currentRound, isCurrentRound);
        setStandingsAndPoints(model, season, currentRound, viewingRound, isCurrentRound);
        setTeamsForCurrentRound(model, enriched);
        setHistoricalPredictions(model, null, enriched, isCurrentRound);
        setFixtures(model, currentRound, isCurrentRound);
        setSwapStatus(model, null);
        return resolveView(hxRequest);
    }

    private String buildViewForUser(
            UUID userId,
            String userEmail,
            Integer round,
            String hxRequest,
            Model model,
            AccessMode accessMode) {
        Season season = getActiveSeason();
        Round currentRound = getCurrentRound(season);
        int viewingRound = resolveViewingRound(round, currentRound.getPosition());
        boolean isCurrentRound = viewingRound == currentRound.getPosition();

        SeasonPrediction prediction = seasonPredictionRepo.findByUserAndSeason(userId, season.getId()).orElse(null);
        RoundResult roundResult = loadRoundResult(userId, season, viewingRound, isCurrentRound);

        List<TeamRank> rankings = resolveRankings(season, currentRound.getPosition(), prediction, isCurrentRound,
                roundResult);
        List<PredictionRankDto> enriched = enrichRankings(rankings);

        populateBaseModel(model, resolvePageTitle(accessMode, prediction, isCurrentRound),
                viewingRound, currentRound.getPosition(), isCurrentRound);
        setRoundState(model, currentRound, isCurrentRound);
        setStandingsAndPoints(model, season, currentRound, viewingRound, isCurrentRound);
        setTeamsForCurrentRound(model, enriched);
        setFixtures(model, currentRound, isCurrentRound);
        setSwapStatus(model, prediction, season, currentRound, isCurrentRound);
        setMessage(model, accessMode, prediction, viewingRound, isCurrentRound, roundResult);
        setUserIdentity(model, userEmail, userId);
        setHistoricalPredictions(model, roundResult, enriched, isCurrentRound);

        return resolveView(hxRequest);
    }

    private void populateUserNotFound(Model model, Integer round) {
        Season season = getActiveSeason();
        Round currentRound = getCurrentRound(season);
        int viewingRound = resolveViewingRound(round, currentRound.getPosition());
        boolean isCurrentRound = viewingRound == currentRound.getPosition();

        List<TeamRank> rankings = getFallbackRankings(season, currentRound.getPosition());
        List<PredictionRankDto> enriched = enrichRankings(rankings);

        populateBaseModel(model, "User Not Found", viewingRound, currentRound.getPosition(), isCurrentRound);
        model.addAttribute("message", "User not found");
        setRoundState(model, currentRound, isCurrentRound);
        setStandingsAndPoints(model, season, currentRound, viewingRound, isCurrentRound);
        setTeamsForCurrentRound(model, enriched);
        setFixtures(model, currentRound, isCurrentRound);
        setSwapStatus(model, null);
        setHistoricalPredictions(model, null, enriched, isCurrentRound);
    }

    private void populateBaseModel(
            Model model,
            String pageTitle,
            int viewingRound,
            int currentRound,
            boolean isCurrentRound) {
        model.addAttribute("pageTitle", pageTitle);
        model.addAttribute("viewingRound", viewingRound);
        model.addAttribute("currentRound", currentRound);
        model.addAttribute("isCurrentRound", isCurrentRound);
        model.addAttribute("canSwap", false);
        model.addAttribute("roundState", "open");
        model.addAttribute("predictionsJson", "[]");
        model.addAttribute("isInitialPrediction", false);
        model.addAttribute("currentStandingsJson", "{}");
        model.addAttribute("fixturesJson", "{}");
        model.addAttribute("currentPointsJson", "{}");
        model.addAttribute("predictions", List.of());
        model.addAttribute("swapStatus", null);
        model.addAttribute("roundScore", null);
        model.addAttribute("totalHits", null);
    }

    private String resolvePageTitle(AccessMode accessMode, SeasonPrediction prediction, boolean isCurrentRound) {
        if (accessMode == AccessMode.ME) {
            if (prediction == null && isCurrentRound) {
                return "Create Prediction";
            }
            return "My Predictions";
        }
        return "User Predictions";
    }

    private void setUserIdentity(Model model, String userEmail, UUID userId) {
        if (userEmail != null) {
            model.addAttribute("userEmail", userEmail);
            return;
        }

        userRepo.findById(userId).ifPresent(user -> model.addAttribute("targetDisplayName", user.getDisplayName()));
        userRepo.findById(userId).ifPresent(user -> model.addAttribute("userPublicId", user.getPublicId().value()));
    }

    private void setMessage(
            Model model,
            AccessMode accessMode,
            SeasonPrediction prediction,
            int viewingRound,
            boolean isCurrentRound,
            RoundResult roundResult) {
        if (!isCurrentRound) {
            model.addAttribute("message", "Viewing Gameweek " + viewingRound + " results");
            return;
        }
        if (accessMode == AccessMode.ME && prediction == null) {
            model.addAttribute("message", "Arrange teams and submit to join the competition");
            return;
        }
        if (roundResult != null) {
            model.addAttribute("message", "Viewing Gameweek " + viewingRound + " results");
            return;
        }
        model.addAttribute("message", null);
    }

    private void setRoundState(Model model, Round currentRound, boolean isCurrentRound) {
        if (!isCurrentRound) {
            model.addAttribute("roundState", RoundStatus.FINALISED.name().toLowerCase());
            return;
        }
        RoundStatus status = resolveRoundStatus(currentRound);
        model.addAttribute("roundState", status.name().toLowerCase());
    }

    private void setStandingsAndPoints(
            Model model,
            Season season,
            Round currentRound,
            int viewingRound,
            boolean isCurrentRound) {
        int targetRound = isCurrentRound ? currentRound.getPosition() : viewingRound;
        standingsRepo.findBySeasonAndRoundPosition(season.getId(), targetRound).ifPresent(standings -> {
            Map<String, Integer> positions = standings.getRankings().stream()
                    .collect(Collectors.toMap(rank -> rank.getRanking().getCode(), rank -> rank.getRanking().getPosition()));
            Map<String, Integer> points = standings.getRankings().stream()
                    .collect(Collectors.toMap(rank -> rank.getRanking().getCode(), rank -> rank.getMetadata().points()));
            writeJson(model, "currentStandingsJson", positions);
            writeJson(model, "currentPointsJson", points);
        });
    }

    private void setTeamsForCurrentRound(Model model, List<PredictionRankDto> enriched) {
        model.addAttribute("predictionsJson", writeJson(enriched));
        model.addAttribute("teams", enriched);
    }

    private void setFixtures(Model model, Round currentRound, boolean isCurrentRound) {
        if (!isCurrentRound) {
            model.addAttribute("fixturesJson", "{}");
            return;
        }

        List<Match> matches = matchRepo.findByRoundIdWithTeams(currentRound.getId());
        Map<String, List<FixtureView>> fixtures = matches.stream()
                .filter(Match::hasTeamsLoaded)
                .flatMap(match -> buildFixtureViews(match).stream())
                .collect(Collectors.groupingBy(FixtureView::teamCode));
        model.addAttribute("fixturesJson", writeJson(fixtures));
    }

    private List<FixtureView> buildFixtureViews(Match match) {
        Team home = match.getHomeTeam();
        Team away = match.getAwayTeam();

        return List.of(
                new FixtureView(home.getCode(), away.getCode(), false, match.getStatus().name()),
                new FixtureView(away.getCode(), home.getCode(), true, match.getStatus().name()));
    }

    private void setSwapStatus(Model model, SeasonPrediction prediction, Season season, Round currentRound,
            boolean isCurrentRound) {
        if (!isCurrentRound || prediction == null) {
            model.addAttribute("swapStatus", null);
            model.addAttribute("canSwap", false);
            return;
        }

        var result = getPredictionUseCase.execute(prediction.getUserId()).getOrElse(null);
        if (result == null) {
            model.addAttribute("swapStatus", null);
            model.addAttribute("canSwap", false);
            return;
        }

        var swapStatus = new SwapStatusView(
                result.swapStatus().canSwap(),
                result.swapStatus().blockedReason(),
                result.swapStatus().nextSwapAt() == null ? null : result.swapStatus().nextSwapAt().toString(),
                prediction.getLastSwapAt() == null ? "Never" : prediction.getLastSwapAt().toString(),
                result.swapStatus().hoursUntilNext(),
                result.swapStatus().canSwap()
                        ? (result.swapStatus().blockedReason() == null ? "You can make changes now!"
                                : result.swapStatus().blockedReason())
                        : buildCooldownMessage(result.swapStatus().hoursUntilNext()));

        model.addAttribute("swapStatus", swapStatus);
        model.addAttribute("canSwap", swapStatus.canSwap());
        model.addAttribute("isInitialPrediction", prediction.getLastSwapAt() == null);
    }

    private void setHistoricalPredictions(
            Model model,
            RoundResult roundResult,
            List<PredictionRankDto> currentPredictions,
            boolean isCurrentRound) {
        if (isCurrentRound) {
            model.addAttribute("predictions", List.of());
            return;
        }

        if (roundResult == null) {
            model.addAttribute("predictions", List.of());
            return;
        }

        List<HistoricalPredictionView> history = roundResult.getRankings().stream()
                .map(rank -> mapHistoricalPrediction(rank, currentPredictions))
                .toList();
        model.addAttribute("predictions", history);
        model.addAttribute("roundScore", roundResult.getTotalScore());
        model.addAttribute("totalHits", history.stream().filter(p -> p.hit() != null).mapToInt(HistoricalPredictionView::hit)
                .sum());
    }

    private HistoricalPredictionView mapHistoricalPrediction(
            ResultTeamRank rank,
            List<PredictionRankDto> currentPredictions) {
        TeamRank teamRank = rank.getRanking();
        PredictionRankDto team = currentPredictions.stream()
                .filter(item -> item.getTeamCode().equals(teamRank.getCode()))
                .findFirst()
                .orElseGet(() -> PredictionRankDto.builder()
                        .position(teamRank.getPosition())
                        .teamCode(teamRank.getCode())
                        .teamName(teamRank.getCode())
                        .teamShortName(teamRank.getCode())
                        .teamSlug(teamRank.getCode())
                        .teamTla(teamRank.getCode())
                        .build());

        return new HistoricalPredictionView(
                teamRank.getPosition(),
                team.getTeamCode(),
                team.getTeamName(),
                rank.getHit(),
                rank.getStandingsPosition());
    }

    private List<TeamRank> resolveRankings(
            Season season,
            int currentRound,
            SeasonPrediction prediction,
            boolean isCurrentRound,
            RoundResult roundResult) {
        if (prediction != null && isCurrentRound) {
            return prediction.getCurrentRankings();
        }

        if (!isCurrentRound && roundResult != null) {
            return roundResult.getRankings().stream().map(ResultTeamRank::getRanking).toList();
        }

        if (prediction != null) {
            return prediction.getCurrentRankings();
        }

        return getFallbackRankings(season, currentRound);
    }

    private List<TeamRank> getFallbackRankings(Season season, int currentRound) {
        return standingsRepo.findBySeasonAndRoundPosition(season.getId(), currentRound)
                .map(standings -> standings.getRankings().stream().map(rank -> rank.getRanking()).toList())
                .orElseGet(() -> season.getInitialRankings() == null ? List.of() : season.getInitialRankings());
    }

    private RoundResult loadRoundResult(UUID userId, Season season, int viewingRound, boolean isCurrentRound) {
        if (isCurrentRound) {
            return null;
        }

        return roundSubmissionRepo
                .findByUserAndSeasonAndRound(userId, season.getId(), viewingRound)
                .flatMap(submission -> roundResultRepo.findByRoundSubmissionId(submission.getId()))
                .orElse(null);
    }

    private List<PredictionRankDto> enrichRankings(List<TeamRank> ranks) {
        if (ranks == null || ranks.isEmpty()) {
            return List.of();
        }

        Map<String, Team> teamsByCode = teamRepo.findAllByCodes(
                ranks.stream().map(TeamRank::getCode).collect(Collectors.toSet()))
                .stream()
                .collect(Collectors.toMap(Team::getCode, Function.identity()));
        return PredictionRankDto.listOf(ranks, teamsByCode);
    }

    private Season getActiveSeason() {
        return seasonRepo.findMostRecentSeason(competitionDefaults.defaultCompetitionSlug())
                .orElseThrow(() -> new IllegalStateException("No active season available"));
    }

    private Round getCurrentRound(Season season) {
        UUID currentRoundId = season.getCurrentRoundId();
        if (currentRoundId == null) {
            throw new IllegalStateException("Season has no current round");
        }

        return roundRepo.findById(currentRoundId)
                .orElseThrow(() -> new IllegalStateException("Current round not found"));
    }

    private int resolveViewingRound(Integer requested, int currentRound) {
        if (requested == null || requested < 1) {
            return currentRound;
        }
        return Math.min(requested, currentRound);
    }

    private RoundStatus resolveRoundStatus(Round round) {
        if (round.isFinalized()) {
            return RoundStatus.FINALISED;
        }

        List<Match> matches = matchRepo.findByRoundId(round.getId());
        if (matches == null || matches.isEmpty()) {
            return RoundStatus.OPEN;
        }

        boolean anyFinished = matches.stream().anyMatch(match -> match.getStatus() == MatchStatus.FINISHED);
        return anyFinished ? RoundStatus.LOCKED : RoundStatus.OPEN;
    }

    private UUID resolveTargetUserId(String userId, Model model) {
        try {
            PublicId publicId = PublicId.create(userId);
            return userRepo.findByPublicId(publicId).map(user -> user.getId()).orElse(null);
        } catch (IllegalArgumentException ex) {
            model.addAttribute("message", "User not found");
            return null;
        }
    }

    private String writeJson(Object payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize JSON payload", e);
            return "{}";
        }
    }

    private void writeJson(Model model, String attribute, Object payload) {
        model.addAttribute(attribute, writeJson(payload));
    }

    private String buildCooldownMessage(Double hoursRemaining) {
        if (hoursRemaining == null) {
            return "Cooldown active.";
        }
        String hours = String.format("%.1f", hoursRemaining);
        return "Cooldown active. You've submitted changes for this period. Next change in " + hours + ".";
    }

    private String resolveView(String hxRequest) {
        return isHtmxRequest(hxRequest) ? "prediction/me :: predictionPage" : "prediction/me";
    }

    private String redirectToGuest(Integer round) {
        return round == null ? "redirect:/prediction/user/guest"
                : "redirect:/prediction/user/guest?round=" + round;
    }

    private boolean isHtmxRequest(String hxRequest) {
        return hxRequest != null && !hxRequest.isBlank();
    }

    private enum AccessMode {
        ME,
        VIEW_OTHER
    }

    private record SwapStatusView(
            boolean canSwap,
            String blockedReason,
            String nextSwapAt,
            String lastSwapAt,
            Double hoursUntilNext,
            String message) {
    }

    private record FixtureView(String teamCode, String opponent, boolean home, String status) {
    }

    private record HistoricalPredictionView(
            int position,
            String teamCode,
            String teamName,
            Integer hit,
            int actualPosition) {
    }
}
