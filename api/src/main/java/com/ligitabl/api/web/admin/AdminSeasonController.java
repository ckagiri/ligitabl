package com.ligitabl.api.web.admin;

import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;

import com.ligitabl.api.config.CompetitionDefaults;
import com.ligitabl.api.rest.season.admin.ActivateSeasonUseCase;
import com.ligitabl.api.rest.season.admin.UpdateSeasonDatesUseCase;
import com.ligitabl.model.domain.Competition;
import com.ligitabl.model.domain.Season;
import com.ligitabl.model.repo.CompetitionRepo;
import com.ligitabl.model.repo.SeasonRepo;

import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Controller
@RequestMapping("/admin")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
@Slf4j
public class AdminSeasonController {

    private final CompetitionDefaults competitionDefaults;
    private final CompetitionRepo competitionRepo;
    private final SeasonRepo seasonRepo;
    private final ActivateSeasonUseCase activateSeasonUseCase;
    private final UpdateSeasonDatesUseCase updateSeasonDatesUseCase;

    @GetMapping("/seasons")
    public String seasonsPage(Model model) {
        Competition competition = competitionRepo
                .findBySlug(competitionDefaults.defaultCompetitionSlug())
                .orElse(null);

        if (competition == null) {
            model.addAttribute("pageTitle", "Season Management");
            return "admin/seasons";
        }

        Season activeSeason = competition.getActiveSeasonId() != null
                ? seasonRepo.findById(competition.getActiveSeasonId()).orElse(null)
                : null;

        Season upcomingSeason = activeSeason != null
                ? seasonRepo.findUpcomingSeason(competition.getId(), activeSeason.getId()).orElse(null)
                : null;

        List<Season> allSeasons = seasonRepo.findAllByCompetitionId(competition.getId());

        model.addAttribute("pageTitle", "Season Management");
        model.addAttribute("competition", competition);
        model.addAttribute("activeSeason", activeSeason);
        model.addAttribute("upcomingSeason", upcomingSeason);
        model.addAttribute("allSeasons", allSeasons);
        model.addAttribute("now", OffsetDateTime.now());

        if (activeSeason != null && activeSeason.getPreSeasonOpensAt() != null) {
            long daysToPreSeason = ChronoUnit.DAYS.between(
                    OffsetDateTime.now(), activeSeason.getPreSeasonOpensAt());
            model.addAttribute("daysToPreSeason", Math.max(0, daysToPreSeason));
        }

        if (upcomingSeason != null && upcomingSeason.getPredictionsOpenAt() != null) {
            long daysToPredictions = ChronoUnit.DAYS.between(
                    OffsetDateTime.now(), upcomingSeason.getPredictionsOpenAt());
            model.addAttribute("daysToPredictions", Math.max(0, daysToPredictions));
        }

        return "admin/seasons";
    }

    @PatchMapping("/competitions/{slug}/activate-season")
    @ResponseBody
    public ResponseEntity<?> activateSeason(
            @PathVariable String slug, @RequestBody ActivateSeasonRequest request) {
        var result = activateSeasonUseCase.execute(slug, request.seasonId(), request.predictionsOpenAt());
        return switch (result) {
            case ActivateSeasonUseCase.Result.Ok ok -> ResponseEntity.ok().build();
            case ActivateSeasonUseCase.Result.CompetitionNotFound e ->
                    ResponseEntity.notFound().build();
            case ActivateSeasonUseCase.Result.SeasonNotFound e ->
                    ResponseEntity.notFound().build();
            case ActivateSeasonUseCase.Result.SeasonNotBelongToCompetition e ->
                    ResponseEntity.badRequest().build();
        };
    }

    @PatchMapping("/competitions/{slug}/season-dates")
    @ResponseBody
    public ResponseEntity<?> updateSeasonDates(
            @PathVariable String slug, @RequestBody UpdateSeasonDatesRequest request) {
        var result = updateSeasonDatesUseCase.execute(
                request.outgoingSeasonId(),
                request.preSeasonOpensAt(),
                request.upcomingSeasonId(),
                request.predictionsOpenAt());
        return switch (result) {
            case UpdateSeasonDatesUseCase.Result.Ok ok -> ResponseEntity.ok().build();
            case UpdateSeasonDatesUseCase.Result.OutgoingSeasonNotFound e ->
                    ResponseEntity.notFound().build();
            case UpdateSeasonDatesUseCase.Result.UpcomingSeasonNotFound e ->
                    ResponseEntity.notFound().build();
            case UpdateSeasonDatesUseCase.Result.InvalidDateOrder e ->
                    ResponseEntity.badRequest().body(e.message());
        };
    }

    public record ActivateSeasonRequest(@NotNull UUID seasonId, OffsetDateTime predictionsOpenAt) {}

    public record UpdateSeasonDatesRequest(
            UUID outgoingSeasonId,
            OffsetDateTime preSeasonOpensAt,
            UUID upcomingSeasonId,
            OffsetDateTime predictionsOpenAt) {}
}
