package com.ligitabl.api.web.contest.contestadmin;

import java.security.Principal;
import java.util.UUID;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import com.ligitabl.api.auth.security.WebUserDetails;
import com.ligitabl.api.rest.contest.deletecontest.DeleteContestUseCase;
import com.ligitabl.api.rest.contest.leavecontest.LeavePrivateContestUseCase;
import com.ligitabl.api.rest.contest.regeneratecontestcode.RegenerateContestCodeUseCase;
import com.ligitabl.api.rest.contest.removecontestmember.RemoveContestMemberUseCase;
import com.ligitabl.api.rest.contest.renewcontest.RenewContestCommand;
import com.ligitabl.api.rest.contest.renewcontest.RenewContestError;
import com.ligitabl.api.rest.contest.renewcontest.RenewContestUseCase;
import com.ligitabl.api.rest.contest.togglecontestjoining.ToggleContestJoiningUseCase;
import com.ligitabl.api.web.contest.shared.ContestSupport;
import com.ligitabl.api.web.shared.security.WebSecurity;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Controller
@RequestMapping("/contests")
@RequiredArgsConstructor
@Slf4j
public class ContestAdminController {

    private final ToggleContestJoiningUseCase toggleContestJoiningUseCase;
    private final RegenerateContestCodeUseCase regenerateContestCodeUseCase;
    private final RemoveContestMemberUseCase removeContestMemberUseCase;
    private final LeavePrivateContestUseCase leavePrivateContestUseCase;
    private final DeleteContestUseCase deleteContestUseCase;
    private final RenewContestUseCase renewContestUseCase;
    private final ContestSupport contestSupport;

    @PostMapping("/{id}/toggle-joining")
    public String toggleJoining(@PathVariable UUID id, Principal principal) {
        WebUserDetails user = WebSecurity.resolveUser(principal);
        if (user == null) return "redirect:/auth/login";

        toggleContestJoiningUseCase
                .execute(id, user.getUserId())
                .fold(
                        error -> {
                            log.warn("Toggle joining error for {}: {}", id, error);
                            return null;
                        },
                        result -> {
                            log.info("Toggled joining for contest {} → isOpen={}", id, result.isOpen());
                            return null;
                        });

        return "redirect:/contests/" + id;
    }

    @PostMapping("/{id}/regenerate-code")
    public String regenerateCode(@PathVariable UUID id, Principal principal) {
        WebUserDetails user = WebSecurity.resolveUser(principal);
        if (user == null) return "redirect:/auth/login";

        regenerateContestCodeUseCase
                .execute(id, user.getUserId())
                .fold(
                        error -> {
                            log.warn("Regenerate code error for {}: {}", id, error);
                            return null;
                        },
                        result -> {
                            log.info("Regenerated code for contest {} → {}", id, result.joinCode());
                            return null;
                        });

        return "redirect:/contests/" + id;
    }

    @PostMapping("/{id}/members/{memberId}/remove")
    public String removeMember(@PathVariable UUID id, @PathVariable UUID memberId, Principal principal) {

        WebUserDetails user = WebSecurity.resolveUser(principal);
        if (user == null) return "redirect:/auth/login";

        int currentRound = contestSupport.resolveCurrentRoundPosition();
        return removeContestMemberUseCase
                .execute(id, user.getUserId(), memberId, currentRound)
                .fold(
                        error -> {
                            log.warn("Remove member error for {}/{}: {}", id, memberId, error);
                            return "redirect:/contests/" + id + "/members?removeBlocked=true";
                        },
                        result -> "redirect:/contests/" + id + "/members");
    }

    @PostMapping("/{id}/leave")
    public String leaveContest(@PathVariable UUID id, Principal principal) {
        WebUserDetails user = WebSecurity.resolveUser(principal);
        if (user == null) return "redirect:/auth/login";

        int currentRound = contestSupport.resolveCurrentRoundPosition();
        return leavePrivateContestUseCase
                .execute(id, user.getUserId(), currentRound)
                .fold(
                        error -> {
                            log.warn("Leave contest error for {}: {}", id, error);
                            return "redirect:/contests/" + id + "?leaveBlocked=true";
                        },
                        result -> "redirect:/contests");
    }

    @PostMapping("/{id}/renew")
    public String renewContest(@PathVariable UUID id, @RequestParam String toSprintCode, Principal principal) {
        WebUserDetails user = WebSecurity.resolveUser(principal);
        if (user == null) return "redirect:/auth/login";

        var cmd = new RenewContestCommand(user.getUserId(), id, toSprintCode);
        return renewContestUseCase
                .execute(cmd)
                .fold(
                        error -> {
                            log.warn("Renew contest error for {}: {}", id, error);
                            String reason = error instanceof RenewContestError.NameConflict
                                    ? "&renewErrorReason=nameConflict"
                                    : "";
                            return "redirect:/contests/" + id + "?renewError=true" + reason;
                        },
                        result -> "redirect:/contests/" + result.renewedContestId() + "?renewed=true");
    }

    @PostMapping("/{id}/delete")
    public String deleteContest(@PathVariable UUID id, Principal principal) {
        WebUserDetails user = WebSecurity.resolveUser(principal);
        if (user == null) return "redirect:/auth/login";

        return deleteContestUseCase
                .execute(id, user.getUserId())
                .fold(
                        error -> {
                            log.warn("Delete contest error for {}: {}", id, error);
                            return "redirect:/contests/" + id + "?deleteError=true";
                        },
                        result -> "redirect:/contests");
    }
}
