package com.ligitabl.api.web;

import java.time.Clock;
import java.time.Instant;
import java.security.Principal;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import com.ligitabl.api.config.CompetitionDefaults;
import com.ligitabl.api.rest.round.shared.RoundSupport;
import com.ligitabl.api.web.predictions.userpredictions.PreviewRankingsSupport;
import com.ligitabl.api.web.shared.dto.TeamRankDto;
import com.ligitabl.api.web.shared.season.SeasonPhase;
import com.ligitabl.model.domain.RoundStatus;
import com.ligitabl.model.domain.Season;
import com.ligitabl.model.domain.Team;
import com.ligitabl.model.domain.TeamRank;
import com.ligitabl.model.repo.SeasonRepo;
import com.ligitabl.model.repo.TeamRepo;

import lombok.RequiredArgsConstructor;

/**
 * Public controller for home page.
 */
@Controller
@RequiredArgsConstructor
public class PublicController {

    private static final int PREVIEW_TEAM_COUNT = 5;

    private final SeasonRepo seasonRepo;
    private final CompetitionDefaults competitionDefaults;
    private final RoundSupport roundSupport;
    private final PreviewRankingsSupport previewRankingsSupport;
    private final TeamRepo teamRepo;
    private final Clock clock;

    @GetMapping("/")
    public String home(Model model, Principal principal) {
        if (principal != null) {
            return "redirect:/my-table";
        }
        model.addAttribute("pageTitle", "Home");
        populateHomeModel(model);
        return "index";
    }

    @GetMapping("/home")
    public String homeNoRedirect(Model model) {
        model.addAttribute("pageTitle", "Home");
        populateHomeModel(model);
        return "index";
    }

    @GetMapping("/about")
    public String about(Model model) {
        model.addAttribute("pageTitle", "About");
        return "about";
    }

    @GetMapping("/faq")
    public String faq(Model model) {
        model.addAttribute("pageTitle", "FAQ");
        return "faq";
    }

    @GetMapping("/favicon.ico")
    public String favicon() {
        return "redirect:/favicon.svg";
    }

    private void populateHomeModel(Model model) {
        Season season = seasonRepo
                .findActiveSeason(competitionDefaults.defaultCompetitionSlug())
                .orElse(null);
        if (season == null) {
            model.addAttribute("showLoggedInCta", false);
            model.addAttribute("previewRankings", List.of());
            return;
        }

        Instant now = clock.instant();
        int currentRound = roundSupport.resolveCurrentRoundPosition();
        RoundStatus currentRoundStatus = roundSupport.currentRoundStatus();

        boolean showLoggedInCta = season.isInPlay(now)
                && currentRound != season.getMaxRounds()
                && currentRoundStatus != RoundStatus.FINALIZED;
        model.addAttribute("showLoggedInCta", showLoggedInCta);

        // Pre-season banner — lets a guest landing during pre-season know registration is
        // open early, or when predictions open.
        SeasonPhase phase = SeasonPhase.resolve(season, now);
        model.addAttribute("isPreSeason", phase.isPreSeason());
        if (phase.daysToPredictions() != null) {
            model.addAttribute("daysToPredictions", phase.daysToPredictions());
        }
        if (phase.predictionsAboutToStart()) {
            model.addAttribute("predictionsAboutToStart", true);
        }
        if (phase.predictionsOpenAt() != null) {
            model.addAttribute("predictionsOpenAt", phase.predictionsOpenAt());
        }

        List<TeamRank> rankings = previewRankingsSupport.getPreviewRankings(season.getId(), currentRound);
        List<TeamRank> topRankings = TeamRank.inPositionOrder(rankings).stream()
                .limit(PREVIEW_TEAM_COUNT)
                .toList();

        Map<String, Team> teamsByCode = teamRepo.findAllTeamsByCode(topRankings);
        model.addAttribute("previewRankings", TeamRankDto.listOf(topRankings, teamsByCode));
    }
}
