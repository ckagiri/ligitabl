package com.ligitabl.api.web;

import java.security.Principal;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import com.ligitabl.api.config.CompetitionDefaults;
import com.ligitabl.api.rest.round.shared.RoundSupport;
import com.ligitabl.api.web.predictions.userpredictions.GetUserPredictionUseCase;
import com.ligitabl.api.web.shared.dto.TeamRankDto;
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
    private final GetUserPredictionUseCase getUserPredictionUseCase;
    private final TeamRepo teamRepo;

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

    /**
     * Populates the "Logged-in CTA" visibility flag and the hero's interactive preview rankings —
     * both need the active season and current round, resolved once here.
     *
     * The CTA only makes sense while there's an active round left to swap in: the season must be
     * in play, the current round must not be the season's last round, and the current round must
     * not have been finalized already (no more swaps possible either way). The preview rankings
     * are the same "previous round or season baseline" fallback shown to guests, trimmed to a
     * short list for display.
     */
    private void populateHomeModel(Model model) {
        Season season = seasonRepo
                .findActiveSeason(competitionDefaults.defaultCompetitionSlug())
                .orElse(null);
        if (season == null) {
            model.addAttribute("showLoggedInCta", false);
            model.addAttribute("previewRankings", List.of());
            return;
        }

        int currentRound = roundSupport.resolveCurrentRoundPosition();
        RoundStatus currentRoundStatus = roundSupport.currentRoundStatus();

        boolean showLoggedInCta = season.isInPlay()
                && currentRound != season.getMaxRounds()
                && currentRoundStatus != RoundStatus.FINALIZED;
        model.addAttribute("showLoggedInCta", showLoggedInCta);

        List<TeamRank> rankings = getUserPredictionUseCase.getPreviewRankings(season.getId(), currentRound);
        List<TeamRank> topRankings = rankings.stream()
                .sorted(Comparator.comparingInt(TeamRank::getPosition))
                .limit(PREVIEW_TEAM_COUNT)
                .toList();

        Map<String, Team> teamsByCode = teamRepo.findAllTeamsByCode(topRankings);
        model.addAttribute("previewRankings", TeamRankDto.listOf(topRankings, teamsByCode));
    }
}
