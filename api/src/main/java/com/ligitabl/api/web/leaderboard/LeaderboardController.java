package com.ligitabl.api.web.leaderboard;

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

import com.ligitabl.api.rest.leaderboard.GetLeaderboardError;
import com.ligitabl.api.rest.leaderboard.GetLeaderboardQuery;
import com.ligitabl.api.rest.leaderboard.GetLeaderboardResult;
import com.ligitabl.api.rest.leaderboard.GetLeaderboardUseCase;
import com.ligitabl.api.rest.leaderboard.GetUserDetailError;
import com.ligitabl.api.rest.leaderboard.GetUserDetailQuery;
import com.ligitabl.api.rest.leaderboard.GetUserDetailResult;
import com.ligitabl.api.rest.leaderboard.GetUserDetailUseCase;
import com.ligitabl.model.auth.Email;
import com.ligitabl.model.domain.LeaderboardEntry;
import com.ligitabl.model.domain.User;
import com.ligitabl.model.repo.UserRepo;

import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Controller
@RequestMapping("/leaderboard")
@RequiredArgsConstructor
@Slf4j
public class LeaderboardController {
    private static final int PAGE_SIZE = 20;

    private final GetLeaderboardUseCase getLeaderboardUseCase;
    private final GetUserDetailUseCase getUserDetailUseCase;
    private final UserRepo userRepo;

    @GetMapping
    public String leaderboard(
            @RequestParam(required = false) String phase,
            @RequestParam(required = false, defaultValue = "1") int page,
            Principal principal,
            Model model,
            HttpServletResponse response,
            @RequestHeader(value = "HX-Request", required = false) String hxRequest) {

        log.info("GET /leaderboard - phase: {}, page: {}", phase, page);

        CurrentUserContext currentUser = resolveCurrentUser(principal);

        int offset = Math.max(0, (page - 1) * PAGE_SIZE);
        var query = new GetLeaderboardQuery(phase, offset, PAGE_SIZE, currentUser.userId());

        return getLeaderboardUseCase
                .execute(query)
                .fold(
                        error -> handleError(error, model, response, hxRequest),
                        result -> handleSuccess(
                                result,
                                page,
                                currentUser.publicId(),
                                currentUser.displayName(),
                                model,
                                hxRequest));
    }

    private record CurrentUserContext(UUID userId, String publicId, String displayName) {
        private static CurrentUserContext empty() {
            return new CurrentUserContext(null, null, null);
        }
    }

    private CurrentUserContext resolveCurrentUser(Principal principal) {
        if (principal == null) {
            return CurrentUserContext.empty();
        }

        try {
            Email email = Email.create(principal.getName());
            User user = userRepo.findByEmail(email).orElse(null);
            if (user == null) {
                return CurrentUserContext.empty();
            }
            return new CurrentUserContext(
                    user.getId(),
                    user.getPublicId().value(),
                    user.getDisplayName());
        } catch (IllegalArgumentException e) {
            log.debug("Could not resolve user from principal: {}", e.getMessage());
            return CurrentUserContext.empty();
        }
    }

    private String handleSuccess(
            GetLeaderboardResult result,
            int page,
            String currentUserPublicId,
            String currentUserName,
            Model model,
            String hxRequest) {

        List<LeaderboardEntry> pageEntries = result.rankings();
        int totalEntries = result.totalParticipants();
        int totalPages = Math.max(1, (int) Math.ceil((double) totalEntries / PAGE_SIZE));
        int offset = result.offset();

        LeaderboardEntry userPosition = result.userEntry();
        boolean userInCurrentPage = result.userInCurrentPage();

        model.addAttribute("leaderboard", pageEntries);
        model.addAttribute("phases", result.allPhases());
        model.addAttribute("currentPhase", result.phase().getCode());
        model.addAttribute("userPosition", userPosition);
        model.addAttribute("userInCurrentPage", userInCurrentPage);
        model.addAttribute("currentUserName", currentUserName);

        // Pagination
        model.addAttribute("currentPage", page);
        model.addAttribute("totalPages", totalPages);
        model.addAttribute("totalEntries", totalEntries);
        model.addAttribute("pageSize", PAGE_SIZE);
        model.addAttribute("hasPreviousPage", result.hasPrevious());
        model.addAttribute("hasNextPage", result.hasNext());
        model.addAttribute("showingFrom", totalEntries > 0 ? offset + 1 : 0);
        model.addAttribute("showingTo", totalEntries > 0 ? Math.min(offset + PAGE_SIZE, totalEntries) : 0);

        if (hxRequest != null && !hxRequest.isBlank()) {
            return "leaderboard :: leaderboardContent";
        }
        return "leaderboard";
    }

    // ========== User Detail Drill-Down ==========

    @GetMapping("/user/{userId}/details")
    public String userDetails(
            @PathVariable String userId,
            @RequestParam(required = false) String phase,
            Model model,
            HttpServletResponse response) {

        log.info("GET /leaderboard/user/{}/details - phase: {}", userId, phase);

        var query = new GetUserDetailQuery(userId, phase);

        return getUserDetailUseCase
                .execute(query)
                .fold(
                        error -> handleUserDetailError(error, model, response),
                        result -> handleUserDetailSuccess(result, model));
    }

    private String handleUserDetailSuccess(GetUserDetailResult result, Model model) {
        model.addAttribute("user", result);
        model.addAttribute("round", result.effectiveRound());
        model.addAttribute("status", result.showingPreviousRound() ? "Finalised" : "");

        return "fragments/user-detail :: user-details(user=${user}, round=${round}, status=${status})";
    }

    private String handleUserDetailError(GetUserDetailError error, Model model, HttpServletResponse response) {

        int status =
                switch (error) {
                    case GetUserDetailError.UserNotFound e -> 404;
                    case GetUserDetailError.NoFinalizedRounds e -> 404;
                    case GetUserDetailError.NoPredictionFound e -> 404;
                    case GetUserDetailError.LeaderboardError e -> 500;
                };

        String message =
                switch (error) {
                    case GetUserDetailError.UserNotFound e -> "User not found";
                    case GetUserDetailError.NoFinalizedRounds e -> "No finalized rounds yet";
                    case GetUserDetailError.NoPredictionFound e -> "No prediction found for round " + e.round();
                    case GetUserDetailError.LeaderboardError e -> "Could not load leaderboard";
                };

        response.setStatus(status);
        model.addAttribute("error", message);
        return "fragments/error-banner :: banner";
    }

    // ========== Leaderboard Error Handler ==========

    private String handleError(GetLeaderboardError error, Model model, HttpServletResponse response, String hxRequest) {

        int status =
                switch (error) {
                    case GetLeaderboardError.DefaultCompetitionNotFound e -> 404;
                    case GetLeaderboardError.ActiveSeasonNotFound e -> 404;
                    case GetLeaderboardError.MainContestNotFound e -> 404;
                    case GetLeaderboardError.PhasesNotConfigured e -> 500;
                    case GetLeaderboardError.InvalidPhase e -> 400;
                };

        String message =
                switch (error) {
                    case GetLeaderboardError.DefaultCompetitionNotFound e -> e.message();
                    case GetLeaderboardError.ActiveSeasonNotFound e -> e.message();
                    case GetLeaderboardError.MainContestNotFound e -> e.message();
                    case GetLeaderboardError.PhasesNotConfigured e -> "Competition phases not configured";
                    case GetLeaderboardError.InvalidPhase e -> "Invalid phase: " + e.phaseCode();
                };

        response.setStatus(status);
        model.addAttribute("error", message);

        if (hxRequest != null && !hxRequest.isBlank()) {
            return "fragments/error-banner :: banner";
        }
        return "error";
    }
}
