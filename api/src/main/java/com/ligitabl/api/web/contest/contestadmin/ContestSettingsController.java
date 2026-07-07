package com.ligitabl.api.web.contest.contestadmin;

import java.security.Principal;
import java.util.UUID;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import com.ligitabl.api.auth.security.WebUserDetails;
import com.ligitabl.api.rest.contest.renamecontest.RenameContestCommand;
import com.ligitabl.api.rest.contest.renamecontest.RenameContestError;
import com.ligitabl.api.rest.contest.renamecontest.RenameContestUseCase;
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
    private final RenameContestUseCase renameContestUseCase;

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

    @PostMapping("/{id}/rename")
    public String renameContest(@PathVariable UUID id, @RequestParam String name, Principal principal) {
        WebUserDetails user = WebSecurity.resolveUser(principal);
        if (user == null) return "redirect:/auth/login";

        return renameContestUseCase
                .execute(new RenameContestCommand(user.getUserId(), id, name))
                .fold(
                        error -> {
                            log.warn("Rename contest error for {}: {}", id, error);
                            return "redirect:/contests/" + id + "/edit?renameError=" + toErrorReason(error);
                        },
                        result -> "redirect:/contests/" + id + "/edit?renamed=true");
    }

    private static String toErrorReason(RenameContestError error) {
        return switch (error) {
            case RenameContestError.BlankName ignored -> "blank";
            case RenameContestError.NameConflict ignored -> "conflict";
            case RenameContestError.NotOwner ignored -> "denied";
            case RenameContestError.ContestNotFound ignored -> "notFound";
        };
    }
}
