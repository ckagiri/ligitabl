package com.ligitabl.api.web.contest.contestdetail;

import java.security.Principal;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import org.springframework.beans.factory.annotation.Value;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ligitabl.api.auth.security.WebUserDetails;
import com.ligitabl.api.rest.contest.getprivatecontest.GetPrivateContestQuery;
import com.ligitabl.api.rest.contest.getprivatecontest.GetPrivateContestUseCase;
import com.ligitabl.api.web.shared.security.WebSecurity;
import com.ligitabl.model.domain.Entry;
import com.ligitabl.model.repo.UserRepo;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Controller
@RequestMapping("/contests")
@RequiredArgsConstructor
@Slf4j
public class ContestDetailController {

    private final GetPrivateContestUseCase getPrivateContestUseCase;
    private final UserRepo userRepo;
    private final ObjectMapper objectMapper;

    @Value("${ligitabl.frontend.url:http://localhost:8080}")
    private String frontendUrl;

    private record MemberRow(UUID userId, String displayName, boolean isActive, boolean isOwner) {}

    @GetMapping("/{id}")
    public String contestDetail(
            @PathVariable UUID id,
            @RequestParam(required = false) String segment,
            @RequestHeader(value = "HX-Request", required = false) String hxRequest,
            Model model,
            Principal principal) {

        WebUserDetails user = WebSecurity.resolveUser(principal);
        if (user == null) return "redirect:/auth/login";

        String segmentCode = (segment != null && !segment.isBlank()) ? segment : "overall";
        var query = new GetPrivateContestQuery(id, user.getUserId(), segmentCode);

        return getPrivateContestUseCase.execute(query).fold(
                error -> {
                    log.warn("Contest detail error for {}: {}", id, error);
                    model.addAttribute("error", "Contest not found or you're not a member.");
                    return "error";
                },
                detail -> {
                    model.addAttribute("contest", detail.contest());
                    model.addAttribute("selectedSegment", detail.selectedSegment());
                    model.addAttribute("leaderboard", detail.leaderboard());
                    model.addAttribute("isOwner", detail.isOwner());
                    String joinCode = detail.joinCode() != null
                            ? detail.joinCode().toUpperCase()
                            : null;
                    model.addAttribute("joinCode", joinCode);
                    model.addAttribute("inviteUrl",
                            joinCode != null ? frontendUrl + "/i/" + joinCode : null);
                    model.addAttribute("segmentTree", detail.segmentTree());
                    model.addAttribute("members",
                            buildMemberRows(detail.members(), detail.contest().getOwnerId()));
                    model.addAttribute("contestDateLabel", detail.contestDateLabel());
                    model.addAttribute("pageTitle", detail.contest().getName());
                    model.addAttribute("currentSegment", segmentCode);

                    try {
                        model.addAttribute("segmentTreeJson",
                                objectMapper.writeValueAsString(detail.segmentTree()));
                    } catch (JsonProcessingException e) {
                        model.addAttribute("segmentTreeJson", "[]");
                    }

                    if (hxRequest != null && !hxRequest.isBlank()) {
                        return "contest/detail :: leaderboard-content";
                    }
                    return "contest/detail";
                });
    }

    private List<MemberRow> buildMemberRows(List<Entry> members, UUID ownerId) {
        var ids = members.stream().map(Entry::getUserId).toList();
        var namesByUserId = userRepo.findDisplayNamesByIds(ids);
        return members.stream()
                .map(entry -> {
                    UUID uid = entry.getUserId();
                    return new MemberRow(
                            uid,
                            namesByUserId.getOrDefault(uid, "Unknown"),
                            entry.getRemovedAtRound() == null,
                            uid.equals(ownerId));
                })
                .toList();
    }
}
