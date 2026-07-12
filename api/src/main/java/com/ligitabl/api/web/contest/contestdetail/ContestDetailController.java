package com.ligitabl.api.web.contest.contestdetail;

import java.security.Principal;
import java.util.List;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ligitabl.api.auth.security.WebUserDetails;
import com.ligitabl.api.rest.contest.getprivatecontest.GetPrivateContestQuery;
import com.ligitabl.api.rest.contest.getprivatecontest.GetPrivateContestUseCase;
import com.ligitabl.api.rest.contest.renewcontest.GetContestRenewalOptionsResult;
import com.ligitabl.api.rest.contest.renewcontest.GetContestRenewalOptionsUseCase;
import com.ligitabl.api.rest.contest.shared.ContestSeasonSupport;
import com.ligitabl.api.web.contest.shared.ContestSupport;
import com.ligitabl.api.web.shared.security.WebSecurity;
import com.ligitabl.model.domain.Entry;
import com.ligitabl.model.domain.LeaderboardResponse;
import com.ligitabl.model.repo.UserRepo;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Controller
@RequestMapping("/contests")
@RequiredArgsConstructor
@Slf4j
public class ContestDetailController {

    private static final int PAGE_SIZE = 10;

    private final GetPrivateContestUseCase getPrivateContestUseCase;
    private final GetContestRenewalOptionsUseCase getContestRenewalOptionsUseCase;
    private final ContestSeasonSupport contestSeasonSupport;
    private final ContestSupport contestSupport;
    private final UserRepo userRepo;
    private final ObjectMapper objectMapper;

    @Value("${ligitabl.frontend.url:http://localhost:8080}")
    private String frontendUrl;

    @Value("${ligitabl.frontend.share-url:${ligitabl.frontend.url:http://localhost:8080}}")
    private String frontendShareUrl;

    public record MemberRow(UUID userId, String displayName, boolean isActive, boolean isOwner) {}

    @GetMapping("/{id}")
    public String contestDetail(
            @PathVariable UUID id,
            @RequestParam(required = false) String segment,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(required = false) String from,
            @RequestHeader(value = "HX-Request", required = false) String hxRequest,
            Model model,
            Principal principal) {

        WebUserDetails user = WebSecurity.resolveUser(principal);
        if (user == null) return "redirect:/auth/login";

        model.addAttribute("backHref", "profile".equals(from) ? "/profile/my-contests" : "/contests");

        String segmentCode = (segment != null && !segment.isBlank()) ? segment : null;
        var query = new GetPrivateContestQuery(id, user.getUserId(), segmentCode, page);

        return getPrivateContestUseCase
                .execute(query)
                .fold(
                        error -> {
                            log.warn("Contest detail error for {}: {}", id, error);
                            model.addAttribute("error", "Contest not found or you're not a member.");
                            return "error";
                        },
                        detail -> {
                            LeaderboardResponse lb = detail.leaderboard();
                            int totalPages = lb.totalParticipants() == 0
                                    ? 1
                                    : (int) Math.ceil((double) lb.totalParticipants() / PAGE_SIZE);
                            int showingFrom = lb.totalParticipants() == 0 ? 0 : page * PAGE_SIZE + 1;
                            int showingTo = Math.min((page + 1) * PAGE_SIZE, lb.totalParticipants());

                            model.addAttribute("contest", detail.contest());
                            model.addAttribute("selectedSegment", detail.selectedSegment());
                            model.addAttribute("leaderboard", lb);
                            model.addAttribute("isOwner", detail.isOwner());

                            boolean isPastSeason = false;
                            boolean isJoinWindowClosed = false;
                            if (hxRequest == null || hxRequest.isBlank()) {
                                var seasonGate = contestSeasonSupport.resolveSeasonGateStatus(detail.contest());
                                isPastSeason = seasonGate.isPastSeason();
                                isJoinWindowClosed = seasonGate.isJoinWindowClosed();
                            }
                            model.addAttribute("isPastSeason", isPastSeason);
                            model.addAttribute("isJoinWindowClosed", isJoinWindowClosed);

                            GetContestRenewalOptionsResult renewal = GetContestRenewalOptionsResult.hidden();
                            if (detail.isOwner() && (hxRequest == null || hxRequest.isBlank())) {
                                renewal = getContestRenewalOptionsUseCase
                                        .execute(id, user.getUserId())
                                        .fold(error -> GetContestRenewalOptionsResult.hidden(), r -> r);
                            }
                            model.addAttribute("renewal", renewal);
                            model.addAttribute(
                                    "renewalSprints",
                                    renewal.visible() ? contestSupport.resolveSprintOptions() : List.of());
                            model.addAttribute(
                                    "renewalQuarters",
                                    renewal.visible() ? contestSupport.resolveQuarterOptions() : List.of());
                            String joinCode = detail.joinCode() != null
                                    ? detail.joinCode().toUpperCase()
                                    : null;
                            model.addAttribute("joinCode", joinCode);
                            model.addAttribute(
                                    "inviteUrl", joinCode != null ? frontendShareUrl + "/join/" + joinCode : null);
                            model.addAttribute(
                                    "joinUrl", joinCode != null ? frontendShareUrl + "/join?code=" + joinCode : null);
                            model.addAttribute("segmentTree", detail.segmentTree());
                            model.addAttribute(
                                    "members",
                                    buildMemberRows(
                                            detail.members(), detail.contest().getOwnerId()));
                            model.addAttribute("contestDateLabel", detail.contestDateLabel());
                            model.addAttribute("contestPeriodLabel", detail.contestPeriodLabel());
                            model.addAttribute("isOpenForJoining", detail.isOpenForJoining());
                            model.addAttribute("pageTitle", detail.contest().getName());
                            model.addAttribute("currentSegment", detail.currentSegmentCode());

                            // Leaderboard pagination
                            model.addAttribute("currentPage", page);
                            model.addAttribute("totalPages", totalPages);
                            model.addAttribute("totalEntries", lb.totalParticipants());
                            model.addAttribute("hasPreviousPage", page > 0);
                            model.addAttribute("hasNextPage", lb.hasNext());
                            model.addAttribute("showingFrom", showingFrom);
                            model.addAttribute("showingTo", showingTo);

                            // For leaderboard current-user indicator and "from GW" label
                            model.addAttribute("userPosition", lb.userEntry());
                            model.addAttribute("userInCurrentPage", lb.userInCurrentPage());
                            model.addAttribute(
                                    "currentPhaseFrom", detail.selectedSegment().getFrom());
                            model.addAttribute("effectiveToRound", lb.effectiveToRound());
                            if (lb.userEntry() != null) {
                                model.addAttribute(
                                        "currentUserName", lb.userEntry().displayName());
                            }

                            try {
                                model.addAttribute(
                                        "segmentTreeJson", objectMapper.writeValueAsString(detail.segmentTree()));
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
