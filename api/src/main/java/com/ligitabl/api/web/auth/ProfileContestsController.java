package com.ligitabl.api.web.auth;

import java.util.UUID;

import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import com.ligitabl.api.auth.security.WebUserDetails;
import com.ligitabl.api.rest.contest.getprofilecontestlists.GetProfileContestListsQuery;
import com.ligitabl.api.rest.contest.getprofilecontestlists.GetProfileContestListsUseCase;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@ConditionalOnWebApplication
@Controller
@RequestMapping("/profile")
@RequiredArgsConstructor
@Slf4j
public class ProfileContestsController {

    private final GetProfileContestListsUseCase getProfileContestListsUseCase;

    @GetMapping("/my-contests")
    public String myContests(
            @AuthenticationPrincipal WebUserDetails userDetails,
            @RequestParam(defaultValue = "1") int activePage,
            @RequestParam(defaultValue = "1") int pastPage,
            Model model) {
        if (userDetails == null) {
            return "redirect:/auth/login";
        }

        UUID userId = userDetails.getUserId();
        model.addAttribute("pageTitle", "My Contests");

        var result = getProfileContestListsUseCase.execute(
                new GetProfileContestListsQuery(userId, activePage, pastPage));

        model.addAttribute("activeContests", result.activeContests());
        model.addAttribute("activeTotal", result.activeTotal());
        model.addAttribute("activePage", activePage);
        model.addAttribute("activePages", result.activePages());
        model.addAttribute("activeFrom", result.activeFrom());
        model.addAttribute("activeTo", result.activeTo());

        model.addAttribute("pastContests", result.pastContests());
        model.addAttribute("pastTotal", result.pastTotal());
        model.addAttribute("pastPage", pastPage);
        model.addAttribute("pastPages", result.pastPages());
        model.addAttribute("pastFrom", result.pastFrom());
        model.addAttribute("pastTo", result.pastTo());

        return "profile/my-contests";
    }
}
