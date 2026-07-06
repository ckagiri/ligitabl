package com.ligitabl.api.web.contest.contestadmin;

import java.security.Principal;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import com.ligitabl.api.auth.security.WebUserDetails;
import com.ligitabl.api.rest.contest.shared.ContestSeasonSupport;
import com.ligitabl.api.web.contest.contestdetail.ContestDetailController.MemberRow;
import com.ligitabl.api.web.contest.shared.ContestSupport;
import com.ligitabl.api.web.shared.security.WebSecurity;
import com.ligitabl.model.domain.Entry;
import com.ligitabl.model.repo.ContestRepo;
import com.ligitabl.model.repo.EntryRepo;
import com.ligitabl.model.repo.UserRepo;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Controller
@RequestMapping("/contests")
@RequiredArgsConstructor
@Slf4j
public class ContestMembersController {

    private static final int PAGE_SIZE = 20;

    private final ContestRepo contestRepo;
    private final EntryRepo entryRepo;
    private final UserRepo userRepo;
    private final ContestSeasonSupport contestSeasonSupport;
    private final ContestSupport contestSupport;

    @GetMapping("/{id}/members")
    public String contestMembers(
            @PathVariable UUID id, @RequestParam(defaultValue = "0") int page, Model model, Principal principal) {

        WebUserDetails user = WebSecurity.resolveUser(principal);
        if (user == null) return "redirect:/auth/login";

        var contest = contestRepo.findById(id).orElse(null);
        if (contest == null) return "redirect:/contests";

        if (!contest.isOwnedBy(user.getUserId())) {
            return "redirect:/contests/" + id;
        }

        List<Entry> allEntries = entryRepo.findByContestId(id);
        UUID ownerId = contest.getOwnerId();
        var ids = allEntries.stream().map(Entry::getUserId).toList();
        var namesByUserId = userRepo.findDisplayNamesByIds(ids);

        List<MemberRow> allMembers = allEntries.stream()
                .map(entry -> {
                    UUID uid = entry.getUserId();
                    return new MemberRow(
                            uid,
                            namesByUserId.getOrDefault(uid, "Unknown"),
                            entry.getRemovedAtRound() == null,
                            uid.equals(ownerId));
                })
                .toList();

        int total = allMembers.size();
        int totalPages = total == 0 ? 1 : (int) Math.ceil((double) total / PAGE_SIZE);
        int fromIdx = Math.min(page * PAGE_SIZE, total);
        int toIdx = Math.min(fromIdx + PAGE_SIZE, total);
        List<MemberRow> pageMembers = allMembers.subList(fromIdx, toIdx);

        boolean isPastSeason = contestSeasonSupport.isPastSeason(contest);
        boolean isFinalSprintUnderway = !isPastSeason
                && contestSeasonSupport.isFinalSprintUnderway(contest, contestSupport.resolveCurrentRoundPosition());

        model.addAttribute("contest", contest);
        model.addAttribute("isPastSeason", isPastSeason);
        model.addAttribute("isFinalSprintUnderway", isFinalSprintUnderway);
        model.addAttribute("members", pageMembers);
        model.addAttribute("currentPage", page);
        model.addAttribute("totalPages", totalPages);
        model.addAttribute("totalEntries", total);
        model.addAttribute("hasPreviousPage", page > 0);
        model.addAttribute("hasNextPage", toIdx < total);
        model.addAttribute("showingFrom", total == 0 ? 0 : fromIdx + 1);
        model.addAttribute("showingTo", toIdx);
        model.addAttribute("pageTitle", contest.getName() + " — Members");
        return "contest/members";
    }
}
