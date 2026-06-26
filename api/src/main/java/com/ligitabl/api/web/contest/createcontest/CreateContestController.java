package com.ligitabl.api.web.contest.createcontest;

import java.security.Principal;
import java.util.UUID;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import com.ligitabl.api.auth.security.WebUserDetails;
import com.ligitabl.api.config.CompetitionDefaults;
import com.ligitabl.api.rest.contest.createprivatecontest.CreatePrivateContestCommand;
import com.ligitabl.api.rest.contest.createprivatecontest.CreatePrivateContestError;
import com.ligitabl.api.rest.contest.createprivatecontest.CreatePrivateContestUseCase;
import com.ligitabl.api.web.contest.shared.ContestSupport;
import com.ligitabl.api.web.shared.security.WebSecurity;

import lombok.RequiredArgsConstructor;

@Controller
@RequestMapping("/contests")
@RequiredArgsConstructor
public class CreateContestController {

    private final CreatePrivateContestUseCase createPrivateContestUseCase;
    private final ContestSupport contestSupport;
    private final CompetitionDefaults competitionDefaults;

    @GetMapping("/create")
    public String createForm(Model model, Principal principal) {
        WebUserDetails user = WebSecurity.resolveUser(principal);
        if (user == null) return "redirect:/auth/login";

        model.addAttribute("sprints", contestSupport.resolveSprintOptions());
        model.addAttribute("quarters", contestSupport.resolveQuarterOptions());
        model.addAttribute("pageTitle", "Create a Contest");
        return "contest/create";
    }

    @PostMapping
    public String createContest(
            @RequestParam String name,
            @RequestParam(required = false) Integer maxEntries,
            @RequestParam String fromSprintCode,
            @RequestParam String toSprintCode,
            Model model,
            Principal principal) {

        WebUserDetails user = WebSecurity.resolveUser(principal);
        if (user == null) return "redirect:/auth/login";

        UUID userId = user.getUserId();
        var cmd = new CreatePrivateContestCommand(
                userId, name, maxEntries, fromSprintCode, toSprintCode,
                competitionDefaults.defaultCompetitionSlug());

        return createPrivateContestUseCase.execute(cmd).fold(
                error -> {
                    model.addAttribute("error", toErrorMessage(error));
                    model.addAttribute("name", name);
                    model.addAttribute("maxEntries", maxEntries);
                    model.addAttribute("fromSprintCode", fromSprintCode);
                    model.addAttribute("toSprintCode", toSprintCode);
                    model.addAttribute("sprints", contestSupport.resolveSprintOptions());
                    model.addAttribute("quarters", contestSupport.resolveQuarterOptions());
                    model.addAttribute("pageTitle", "Create a Contest");
                    return "contest/create";
                },
                success -> "redirect:/contests/" + success.contestId());
    }

    private static String toErrorMessage(CreatePrivateContestError error) {
        return switch (error) {
            case CreatePrivateContestError.CompetitionNotFound e ->
                    "Competition not found: " + e.slug();
            case CreatePrivateContestError.SeasonNotFound ignored ->
                    "No active season found.";
            case CreatePrivateContestError.CurrentRoundNotFound ignored ->
                    "Could not determine current round.";
            case CreatePrivateContestError.InvalidFromSprint e ->
                    "The selected start sprint (" + e.sprintCode() + ") has already ended or is locked.";
            case CreatePrivateContestError.InvalidToCombination e ->
                    "Invalid contest window: " + e.fromCode() + " → " + e.toCode()
                            + ". The end sprint must be in the same or a following quarter.";
            case CreatePrivateContestError.MaxContestsReached e ->
                    "You've reached the limit of " + e.max() + " private contests.";
        };
    }
}
