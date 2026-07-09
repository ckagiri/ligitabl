package com.ligitabl.api.web;

import java.security.Principal;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import com.ligitabl.api.config.CompetitionDefaults;
import com.ligitabl.api.rest.round.shared.RoundSupport;
import com.ligitabl.model.domain.RoundStatus;
import com.ligitabl.model.domain.Season;
import com.ligitabl.model.repo.SeasonRepo;

import lombok.RequiredArgsConstructor;

/**
 * Public controller for home page.
 */
@Controller
@RequiredArgsConstructor
public class PublicController {

    private final SeasonRepo seasonRepo;
    private final CompetitionDefaults competitionDefaults;
    private final RoundSupport roundSupport;

    @GetMapping("/")
    public String home(Model model, Principal principal) {
        if (principal != null) {
            return "redirect:/my-table";
        }
        model.addAttribute("pageTitle", "Home");
        populateLoggedInCtaModel(model);
        return "index";
    }

    @GetMapping("/home")
    public String homeNoRedirect(Model model) {
        model.addAttribute("pageTitle", "Home");
        populateLoggedInCtaModel(model);
        return "index";
    }

    @GetMapping("/favicon.ico")
    public String favicon() {
        return "redirect:/favicon.svg";
    }

    /**
     * The "Logged-in CTA" section only makes sense while there's an active round left to swap in:
     * the season must be in play, the current round must not be the season's last round, and the
     * current round must not have been finalized already (no more swaps possible either way).
     */
    private void populateLoggedInCtaModel(Model model) {
        Season season =
                seasonRepo.findActiveSeason(competitionDefaults.defaultCompetitionSlug()).orElse(null);
        if (season == null) {
            model.addAttribute("showLoggedInCta", false);
            return;
        }

        int currentRound = roundSupport.resolveCurrentRoundPosition();
        RoundStatus currentRoundStatus = roundSupport.currentRoundStatus();

        boolean showLoggedInCta = season.isInPlay()
                && currentRound != season.getMaxRounds()
                && currentRoundStatus != RoundStatus.FINALIZED;
        model.addAttribute("showLoggedInCta", showLoggedInCta);
    }
}
