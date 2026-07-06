package com.ligitabl.api.web.contest.contestadmin;

import java.security.Principal;
import java.util.UUID;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;

import com.ligitabl.api.auth.security.WebUserDetails;
import com.ligitabl.api.rest.contest.shared.ContestSeasonSupport;
import com.ligitabl.api.web.shared.security.WebSecurity;
import com.ligitabl.model.repo.ContestRepo;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Controller
@RequestMapping("/contests")
@RequiredArgsConstructor
@Slf4j
public class ContestSettingsController {

    private final ContestRepo contestRepo;
    private final ContestSeasonSupport contestSeasonSupport;

    @GetMapping("/{id}/edit")
    public String contestSettings(@PathVariable UUID id, Model model, Principal principal) {

        WebUserDetails user = WebSecurity.resolveUser(principal);
        if (user == null) return "redirect:/auth/login";

        var contest = contestRepo.findById(id).orElse(null);
        if (contest == null) return "redirect:/contests";

        if (!contest.isOwnedBy(user.getUserId())) {
            return "redirect:/contests/" + id;
        }

        model.addAttribute("contest", contest);
        model.addAttribute("isPastSeason", contestSeasonSupport.isPastSeason(contest));
        model.addAttribute("pageTitle", contest.getName() + " — Settings");
        return "contest/edit";
    }
}
