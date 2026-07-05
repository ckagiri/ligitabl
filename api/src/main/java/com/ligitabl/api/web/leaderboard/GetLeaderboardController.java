package com.ligitabl.api.web.leaderboard;

import java.security.Principal;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import com.ligitabl.api.rest.leaderboard.getleaderboard.GetLeaderboardError;
import com.ligitabl.api.rest.leaderboard.getleaderboard.GetLeaderboardQuery;
import com.ligitabl.api.rest.leaderboard.getleaderboard.GetLeaderboardResult;
import com.ligitabl.api.rest.leaderboard.getleaderboard.GetLeaderboardUseCase;
import com.ligitabl.model.auth.Email;
import com.ligitabl.model.domain.LeaderboardEntry;
import com.ligitabl.model.domain.PhaseType;
import com.ligitabl.model.domain.RoundSpan;
import com.ligitabl.model.domain.User;
import com.ligitabl.model.repo.UserRepo;

import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Controller("webGetLeaderboardController")
@RequestMapping("/leaderboard")
@RequiredArgsConstructor
@Slf4j
public class GetLeaderboardController {
    private static final int PAGE_SIZE = 10;

    private final GetLeaderboardUseCase getLeaderboardUseCase;
    private final UserRepo userRepo;

    @GetMapping
    public String getLeaderboard(
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
                                result, page, currentUser.publicId(), currentUser.displayName(), model, hxRequest));
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
            return new CurrentUserContext(user.getId(), user.getPublicId().value(), user.getDisplayName());
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

        model.addAttribute("pageTitle", "Leaderboard");
        model.addAttribute("leaderboard", pageEntries);
        model.addAttribute("phases", result.phases());
        model.addAttribute("currentPhase", result.phase().getCode());
        model.addAttribute("currentPhaseType", result.phase().getType().name());
        model.addAttribute("currentPhaseFrom", result.phase().getFrom());
        RoundSpan currentSprint = result.currentSprint();
        RoundSpan currentQuarter = result.currentQuarter();

        RoundSpan nextSprint = result.phases().stream()
                .filter(p -> p.getType() == PhaseType.SPRINT)
                .filter(p -> currentSprint != null && p.getFrom() == currentSprint.getTo() + 1)
                .findFirst()
                .orElse(null);

        RoundSpan nextQuarter = result.phases().stream()
                .filter(p -> p.getType() == PhaseType.QUARTER)
                .filter(p -> currentQuarter != null && p.getFrom() == currentQuarter.getTo() + 1)
                .findFirst()
                .orElse(null);

        model.addAttribute("currentSprint", currentSprint != null ? currentSprint.getCode() : null);
        model.addAttribute("currentQuarter", currentQuarter != null ? currentQuarter.getCode() : null);
        model.addAttribute("currentSprintFrom", currentSprint != null ? currentSprint.getFrom() : 0);
        model.addAttribute("currentQuarterFrom", currentQuarter != null ? currentQuarter.getFrom() : 0);
        model.addAttribute("nextSprintFrom", nextSprint != null ? nextSprint.getFrom() : null);
        model.addAttribute("nextQuarterFrom", nextQuarter != null ? nextQuarter.getFrom() : null);
        model.addAttribute("isLastSprint", nextSprint == null && currentSprint != null);
        model.addAttribute("isLastQuarter", nextQuarter == null && currentQuarter != null);
        model.addAttribute("effectiveToRound", result.effectiveToRound());
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

    private String handleError(GetLeaderboardError error, Model model, HttpServletResponse response, String hxRequest) {
        ErrorResponse errorResponse =
                switch (error) {
                    case GetLeaderboardError.DefaultCompetitionNotFound e -> new ErrorResponse(404, e.message());
                    case GetLeaderboardError.ActiveSeasonNotFound e -> new ErrorResponse(404, e.message());
                    case GetLeaderboardError.MainContestNotFound e -> new ErrorResponse(404, e.message());
                    case GetLeaderboardError.PhasesNotConfigured __ -> new ErrorResponse(
                            500, "Competition phases not configured");
                    case GetLeaderboardError.InvalidPhase e -> new ErrorResponse(
                            400, "Invalid phase: " + e.phaseCode());
                };

        response.setStatus(errorResponse.status());
        model.addAttribute("error", errorResponse.message());

        if (hxRequest != null && !hxRequest.isBlank()) {
            return "fragments/error-banner :: banner";
        }
        return "error";
    }

    private record ErrorResponse(int status, String message) {}
}
