package com.ligitabl.api.web.admin;

import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.ligitabl.api.config.CompetitionDefaults;
import com.ligitabl.api.rest.admin.deleteuser.DeleteUserUseCase;
import com.ligitabl.model.domain.Competition;
import com.ligitabl.model.domain.SuspicionTier;
import com.ligitabl.model.domain.SuspiciousEmailDetector;
import com.ligitabl.model.domain.User;
import com.ligitabl.model.repo.CompetitionRepo;
import com.ligitabl.model.repo.SeasonPredictionRepo;
import com.ligitabl.model.repo.UserRepo;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Controller
@RequestMapping("/admin")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
@Slf4j
public class AdminUserController {

    private static final int DEFAULT_PAGE_SIZE = 10;
    private static final List<Integer> PAGE_SIZE_OPTIONS = List.of(10, 25, 50, 100);

    private final UserRepo userRepo;
    private final SeasonPredictionRepo seasonPredictionRepo;
    private final CompetitionRepo competitionRepo;
    private final CompetitionDefaults competitionDefaults;
    private final DeleteUserUseCase deleteUserUseCase;

    @GetMapping("/users")
    public String usersPage(
            @RequestParam(required = false, defaultValue = "1") int page,
            @RequestParam(required = false, defaultValue = "" + DEFAULT_PAGE_SIZE) int size,
            Model model,
            @RequestHeader(value = "HX-Request", required = false) String hxRequest) {

        int pageSize = PAGE_SIZE_OPTIONS.contains(size) ? size : DEFAULT_PAGE_SIZE;
        int safePage = Math.max(1, page);
        int offset = (safePage - 1) * pageSize;

        List<User> users = userRepo.findAllPaged(offset, pageSize);
        long totalEntries = userRepo.countAll();
        int totalPages = Math.max(1, (int) Math.ceil((double) totalEntries / pageSize));

        OffsetDateTime now = OffsetDateTime.now();
        Map<UUID, String> lastLoginDisplay = new LinkedHashMap<>();
        for (User u : users) {
            lastLoginDisplay.put(u.getId(), RelativeTimeFormatter.format(u.getLastLoginAt(), now));
        }

        Map<UUID, EngagementInfo> engagement = engagementByUser(users);

        model.addAttribute("pageTitle", "Users");
        model.addAttribute("users", users);
        model.addAttribute("lastLoginDisplay", lastLoginDisplay);
        model.addAttribute("engagement", engagement);
        model.addAttribute("suspicion", suspicionByUser(users, engagement));
        model.addAttribute("currentPage", safePage);
        model.addAttribute("totalPages", totalPages);
        model.addAttribute("totalEntries", totalEntries);
        model.addAttribute("pageSize", pageSize);
        model.addAttribute("pageSizeOptions", PAGE_SIZE_OPTIONS);
        model.addAttribute("hasPreviousPage", safePage > 1);
        model.addAttribute("hasNextPage", safePage < totalPages);
        model.addAttribute("showingFrom", totalEntries > 0 ? offset + 1 : 0);
        model.addAttribute("showingTo", totalEntries > 0 ? Math.min(offset + pageSize, totalEntries) : 0);

        if (hxRequest != null && !hxRequest.isBlank()) {
            return "admin/users :: usersContent";
        }
        return "admin/users";
    }

    @PostMapping("/users/{id}/delete")
    public String deleteUser(
            @PathVariable UUID id,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "" + DEFAULT_PAGE_SIZE) int size,
            RedirectAttributes redirectAttributes) {

        var result = deleteUserUseCase.execute(id);
        if (result instanceof DeleteUserUseCase.Result.Ok) {
            log.info("[ADMIN_DELETE_USER_OK] userId={}", id);
        } else {
            log.warn("[ADMIN_DELETE_USER_FAILED] userId={} result={}", id, result);
            redirectAttributes.addFlashAttribute("failedDeleteIds", Set.of(id));
        }

        return "redirect:/admin/users?page=" + page + "&size=" + size;
    }

    @PostMapping("/users/batch-delete")
    public String batchDeleteUsers(
            @RequestParam(required = false) List<UUID> ids,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "" + DEFAULT_PAGE_SIZE) int size,
            RedirectAttributes redirectAttributes) {

        Set<UUID> failed = new LinkedHashSet<>();
        if (ids != null) {
            for (UUID id : ids) {
                var result = deleteUserUseCase.execute(id);
                if (result instanceof DeleteUserUseCase.Result.Ok) {
                    log.info("[ADMIN_BATCH_DELETE_USER_OK] userId={}", id);
                } else {
                    log.warn("[ADMIN_BATCH_DELETE_USER_FAILED] userId={} result={}", id, result);
                    failed.add(id);
                }
            }
        }

        if (!failed.isEmpty()) {
            redirectAttributes.addFlashAttribute("failedDeleteIds", failed);
        }

        return "redirect:/admin/users?page=" + page + "&size=" + size;
    }

    private Map<UUID, EngagementInfo> engagementByUser(List<User> users) {
        if (users.isEmpty()) {
            return Map.of();
        }

        List<UUID> userIds = users.stream().map(User::getId).toList();
        UUID currentSeasonId = resolveActiveSeasonId();

        Map<UUID, Integer> totalPredictions = seasonPredictionRepo.countByUserIds(userIds);
        Map<UUID, Integer> currentSeasonSwaps = seasonPredictionRepo.sumSwapCountsByUserIdsAndSeason(userIds, currentSeasonId);

        Map<UUID, EngagementInfo> result = new LinkedHashMap<>();
        for (UUID id : userIds) {
            result.put(
                    id,
                    new EngagementInfo(
                            totalPredictions.getOrDefault(id, 0),
                            currentSeasonSwaps.containsKey(id),
                            currentSeasonSwaps.getOrDefault(id, 0)));
        }
        return result;
    }

    private Map<UUID, SuspicionInfo> suspicionByUser(List<User> users, Map<UUID, EngagementInfo> engagement) {
        Map<UUID, SuspicionInfo> result = new LinkedHashMap<>();
        for (User u : users) {
            SuspiciousEmailDetector.Result detected = SuspiciousEmailDetector.analyze(u.getEmail().value());
            boolean eligibleForDelete = engagement.get(u.getId()).eligibleForDelete();
            SuspicionTier tier = SuspicionTier.of(detected, eligibleForDelete);
            result.put(u.getId(), new SuspicionInfo(detected.score(), tier, detected.reasons()));
        }
        return result;
    }

    private UUID resolveActiveSeasonId() {
        Competition competition = competitionRepo
                .findBySlug(competitionDefaults.defaultCompetitionSlug())
                .orElse(null);
        return competition != null ? competition.getActiveSeasonId() : null;
    }
}
