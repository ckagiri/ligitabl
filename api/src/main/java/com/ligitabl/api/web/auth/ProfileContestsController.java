package com.ligitabl.api.web.auth;

import java.util.List;
import java.util.UUID;

import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import com.ligitabl.api.auth.security.WebUserDetails;
import com.ligitabl.api.rest.contest.shared.ContestRankResolver;
import com.ligitabl.model.repo.ContestRepo;
import com.ligitabl.model.repo.EntryRepo;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@ConditionalOnWebApplication
@Controller
@RequestMapping("/profile")
@RequiredArgsConstructor
@Slf4j
public class ProfileContestsController {

    private static final int PAGE_SIZE = 10;

    private final EntryRepo entryRepo;
    private final ContestRepo contestRepo;
    private final ContestRankResolver contestRankResolver;

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
        buildContestLists(userId, activePage, pastPage, model);
        return "profile/my-contests";
    }

    private void buildContestLists(UUID userId, int activePage, int pastPage, Model model) {
        int activeTotal = contestRepo.countContestsByUserId(userId, false);
        int pastTotal = contestRepo.countContestsByUserId(userId, true);

        int activeOffset = (Math.max(1, activePage) - 1) * PAGE_SIZE;
        int pastOffset = (Math.max(1, pastPage) - 1) * PAGE_SIZE;

        List<ContestSummary> activeContests =
                contestRepo.findContestsByUserId(userId, false, PAGE_SIZE, activeOffset).stream()
                        .map(v -> toSummary(v, userId))
                        .toList();

        List<ContestSummary> pastContests =
                contestRepo.findContestsByUserId(userId, true, PAGE_SIZE, pastOffset).stream()
                        .map(v -> toSummary(v, userId))
                        .toList();

        model.addAttribute("activeContests", activeContests);
        model.addAttribute("activeTotal", activeTotal);
        model.addAttribute("activePage", activePage);
        model.addAttribute("activePages", (int) Math.ceil((double) activeTotal / PAGE_SIZE));
        model.addAttribute("activeFrom", activeTotal == 0 ? 0 : activeOffset + 1);
        model.addAttribute("activeTo", Math.min(activeOffset + PAGE_SIZE, activeTotal));

        model.addAttribute("pastContests", pastContests);
        model.addAttribute("pastTotal", pastTotal);
        model.addAttribute("pastPage", pastPage);
        model.addAttribute("pastPages", (int) Math.ceil((double) pastTotal / PAGE_SIZE));
        model.addAttribute("pastFrom", pastTotal == 0 ? 0 : pastOffset + 1);
        model.addAttribute("pastTo", Math.min(pastOffset + PAGE_SIZE, pastTotal));
    }

    private ContestSummary toSummary(ContestRepo.UserContestView view, UUID userId) {
        int memberCount = entryRepo.countActiveByContestId(view.contestId());
        Integer rank = resolveRank(view, userId);
        String link = "/contests/" + view.contestId() + (view.isPrivate() ? "" : "?segment=overall");
        return new ContestSummary(view.contestName(), view.seasonName(), memberCount, rank, link);
    }

    public record ContestSummary(String contestName, String seasonName, int memberCount, Integer rank, String link) {}

    private Integer resolveRank(ContestRepo.UserContestView view, UUID userId) {
        try {
            return contestRankResolver
                    .resolve(
                            view.contestId(), view.seasonId(), view.fromRoundPosition(), view.toRoundPosition(), userId)
                    .position();
        } catch (Exception e) {
            log.warn("Could not resolve rank for user {} in contest {}: {}", userId, view.contestId(), e.getMessage());
            return null;
        }
    }
}
