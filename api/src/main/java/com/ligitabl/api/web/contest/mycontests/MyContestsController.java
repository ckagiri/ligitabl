package com.ligitabl.api.web.contest.mycontests;

import java.security.Principal;
import java.util.UUID;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import com.ligitabl.api.auth.security.WebUserDetails;
import com.ligitabl.api.config.CompetitionDefaults;
import com.ligitabl.api.rest.contest.getusercontestsummary.GetUserContestSummaryQuery;
import com.ligitabl.api.rest.contest.getusercontestsummary.GetUserContestSummaryUseCase;
import com.ligitabl.api.web.shared.security.WebSecurity;

import lombok.RequiredArgsConstructor;

@Controller
@RequestMapping("/contests")
@RequiredArgsConstructor
public class MyContestsController {

    private final GetUserContestSummaryUseCase getUserContestSummaryUseCase;
    private final CompetitionDefaults competitionDefaults;

    @GetMapping
    public String myContests(Model model, Principal principal) {
        WebUserDetails user = WebSecurity.resolveUser(principal);
        if (user == null) return "redirect:/auth/login";

        UUID userId = user.getUserId();
        var result = getUserContestSummaryUseCase.execute(
                new GetUserContestSummaryQuery(userId, competitionDefaults.defaultCompetitionSlug()));

        model.addAttribute("generalContests", result.generalContests());
        model.addAttribute("privateContests", result.privateContests());
        model.addAttribute("pageTitle", "My Contests");
        return "contests";
    }
}
