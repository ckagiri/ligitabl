package com.ligitabl.api.web.matches;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import com.ligitabl.api.config.CompetitionDefaults;
import com.ligitabl.api.rest.matchadmin.reschedulematch.RescheduleMatchCommand;
import com.ligitabl.api.rest.matchadmin.reschedulematch.RescheduleMatchUseCase;
import com.ligitabl.api.rest.matchadmin.transitionmatchstatus.TransitionMatchCommand;
import com.ligitabl.api.rest.matchadmin.transitionmatchstatus.TransitionMatchStatusUseCase;
import com.ligitabl.api.rest.matchadmin.updatekickoff.UpdateMatchKickoffUseCase;
import com.ligitabl.api.rest.shared.HierarchyValidator;
import com.ligitabl.model.domain.Match;
import com.ligitabl.model.domain.MatchStatus;
import com.ligitabl.model.repo.MatchRepo;
import com.ligitabl.model.repo.RoundRepo;

import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Controller("webMatchAdminController")
@RequestMapping("/matches")
@RequiredArgsConstructor
@Slf4j
public class MatchAdminController {

    private final HierarchyValidator hierarchyValidator;
    private final MatchRepo matchRepo;
    private final RoundRepo roundRepo;
    private final TransitionMatchStatusUseCase transitionUseCase;
    private final RescheduleMatchUseCase rescheduleUseCase;
    private final UpdateMatchKickoffUseCase updateKickoffUseCase;
    private final CompetitionDefaults competitionDefaults;

    public record RoundOption(int position, String label) {}

    @GetMapping("/{matchSlug}/admin-modal")
    @PreAuthorize("hasRole('ADMIN')")
    public String adminModal(
            @PathVariable String matchSlug,
            @RequestParam Integer round,
            @RequestParam(required = false) String home,
            @RequestParam(required = false) String away,
            Model model,
            HttpServletResponse response) {

        log.info("GET /matches/{}/admin-modal, round={}", matchSlug, round);

        var hierarchyResult = hierarchyValidator.resolveHierarchy(competitionDefaults.defaultCompetitionSlug(), round);
        if (hierarchyResult.isLeft()) {
            response.setStatus(400);
            model.addAttribute("error", hierarchyResult.getLeft().getMessage());
            return "fragments/match-admin-modal :: error-message";
        }
        var ctx = hierarchyResult.get();

        var matchOpt = matchRepo.findByRoundIdAndSlug(ctx.round().getId(), matchSlug);
        if (matchOpt.isEmpty()) {
            response.setStatus(404);
            model.addAttribute("error", "Match not found");
            return "fragments/match-admin-modal :: error-message";
        }
        var match = matchOpt.get();

        MatchStatus status = match.getStatus();
        boolean canReschedule = status == MatchStatus.SCHEDULED || status == MatchStatus.POSTPONED;

        boolean canEditKickoff = status != MatchStatus.FINISHED && status != MatchStatus.LIVE;

        model.addAttribute("matchSlug", matchSlug);
        model.addAttribute("matchLabel", buildLabel(home, away));
        model.addAttribute("currentStatus", status.name());
        model.addAttribute("validTransitions", Match.validTransitionsFrom(status));
        model.addAttribute("canReschedule", canReschedule);
        model.addAttribute("canEditKickoff", canEditKickoff);
        model.addAttribute("round", round);

        if (canEditKickoff && match.getKickOff() != null) {
            model.addAttribute(
                    "currentKickOffUtc",
                    match.getKickOff()
                            .withOffsetSameInstant(ZoneOffset.UTC)
                            .toInstant()
                            .toString());
        }

        if (canReschedule) {
            int floorPosition = hierarchyValidator
                    .validateCurrentRound(ctx.season())
                    .fold(__ -> round, currentRound -> currentRound.getPosition());

            var availableRounds = roundRepo
                    .findBySeasonIdOrderByPosition(ctx.season().getId())
                    .stream()
                    .filter(r -> r.getPosition() >= floorPosition && !r.isFinalized() && r.getPosition() != round)
                    .map(r -> new RoundOption(r.getPosition(), "GW " + r.getPosition()))
                    .toList();
            model.addAttribute("availableRounds", availableRounds);
        }

        return "fragments/match-admin-modal :: modal";
    }

    @PostMapping("/{matchSlug}/transition")
    @PreAuthorize("hasRole('ADMIN')")
    public String transition(
            @PathVariable String matchSlug,
            @RequestParam Integer round,
            @RequestParam String newStatus,
            @RequestParam(required = false) Integer homeGoals,
            @RequestParam(required = false) Integer awayGoals,
            Model model,
            HttpServletResponse response) {

        log.info("POST /matches/{}/transition, round={}, newStatus={}", matchSlug, round, newStatus);

        MatchStatus status;
        try {
            status = MatchStatus.valueOf(newStatus);
        } catch (IllegalArgumentException e) {
            response.setStatus(400);
            model.addAttribute("error", "Invalid status: " + newStatus);
            return "fragments/match-admin-modal :: error-message";
        }

        TransitionMatchCommand.ScoreDto score = null;
        if (status == MatchStatus.FINISHED) {
            if (homeGoals == null || awayGoals == null || homeGoals < 0 || awayGoals < 0) {
                response.setStatus(400);
                model.addAttribute("error", "Valid scores are required for FINISHED status");
                return "fragments/match-admin-modal :: error-message";
            }
            score = new TransitionMatchCommand.ScoreDto(homeGoals, awayGoals);
        }

        var cmd = TransitionMatchCommand.builder()
                .matchSlug(matchSlug)
                .roundPosition(round)
                .newStatus(status)
                .reason(null)
                .score(score)
                .build();

        return transitionUseCase
                .execute(cmd)
                .fold(
                        err -> {
                            log.warn("Transition failed for {}: {}", matchSlug, err.getMessage());
                            response.setStatus(422);
                            model.addAttribute("error", err.getMessage());
                            return "fragments/match-admin-modal :: error-message";
                        },
                        __ -> {
                            response.setHeader("HX-Trigger", "matchSyncComplete");
                            return "fragments/match-admin-modal :: done";
                        });
    }

    @PostMapping("/{matchSlug}/move")
    @PreAuthorize("hasRole('ADMIN')")
    public String move(
            @PathVariable String matchSlug,
            @RequestParam Integer round,
            @RequestParam Integer newRoundPosition,
            Model model,
            HttpServletResponse response) {

        log.info("POST /matches/{}/move, round={}, toRound={}", matchSlug, round, newRoundPosition);

        var cmd = RescheduleMatchCommand.builder()
                .matchSlug(matchSlug)
                .roundPosition(round)
                .newRoundPosition(newRoundPosition)
                .reason(null)
                .build();

        return rescheduleUseCase
                .execute(cmd)
                .fold(
                        err -> {
                            log.warn("Move failed for {}: {}", matchSlug, err.getMessage());
                            response.setStatus(422);
                            model.addAttribute("error", err.getMessage());
                            return "fragments/match-admin-modal :: error-message";
                        },
                        __ -> {
                            response.setHeader("HX-Trigger", "matchSyncComplete");
                            return "fragments/match-admin-modal :: done";
                        });
    }

    @PostMapping("/{matchSlug}/update-kickoff")
    @PreAuthorize("hasRole('ADMIN')")
    public String updateKickoff(
            @PathVariable String matchSlug,
            @RequestParam Integer round,
            @RequestParam String kickOffDate,
            @RequestParam String kickOffTime,
            @RequestParam Integer utcOffset,
            Model model,
            HttpServletResponse response) {

        log.info(
                "POST /matches/{}/update-kickoff, round={}, date={}, time={}, utcOffset={}",
                matchSlug,
                round,
                kickOffDate,
                kickOffTime,
                utcOffset);

        if (kickOffDate == null || kickOffDate.isBlank() || kickOffTime == null || kickOffTime.isBlank()) {
            response.setStatus(400);
            model.addAttribute("error", "Date and time are required");
            return "fragments/match-admin-modal :: error-message";
        }

        OffsetDateTime newKickOff;
        try {
            // utcOffset is minutes WEST of UTC (browser's getTimezoneOffset()):
            // UTC+2 → utcOffset = -120, ZoneOffset = +02:00
            ZoneOffset zoneOffset = ZoneOffset.ofTotalSeconds(-utcOffset * 60);
            newKickOff = OffsetDateTime.of(LocalDateTime.parse(kickOffDate + "T" + kickOffTime + ":00"), zoneOffset);
        } catch (DateTimeParseException e) {
            response.setStatus(400);
            model.addAttribute("error", "Invalid date or time format");
            return "fragments/match-admin-modal :: error-message";
        }

        var cmd = new UpdateMatchKickoffUseCase.Command(round, matchSlug, newKickOff);

        return updateKickoffUseCase
                .execute(cmd)
                .fold(
                        err -> {
                            log.warn("Kickoff update failed for {}: {}", matchSlug, err.getMessage());
                            response.setStatus(422);
                            model.addAttribute("error", err.getMessage());
                            return "fragments/match-admin-modal :: error-message";
                        },
                        __ -> {
                            response.setHeader("HX-Trigger", "matchSyncComplete");
                            return "fragments/match-admin-modal :: done";
                        });
    }

    private static String buildLabel(String home, String away) {
        String h = home != null && !home.isBlank() ? home : "?";
        String a = away != null && !away.isBlank() ? away : "?";
        return h + " · " + a;
    }
}
