package com.ligitabl.api.web.auth;

import com.ligitabl.api.auth.security.WebUserDetails;
import com.ligitabl.api.config.CompetitionDefaults;
import com.ligitabl.model.auth.Email;
import com.ligitabl.model.domain.Season;
import com.ligitabl.model.domain.User;
import com.ligitabl.model.repo.ContestRepo;
import com.ligitabl.model.repo.SeasonRepo;
import com.ligitabl.model.repo.UserRepo;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

import java.security.Principal;
import java.util.Optional;
import java.util.UUID;

/**
 * Controller advice to provide navbar context across all views.
 *
 * <p>Provides:
 * <ul>
 *   <li>hasContestEntry - true if logged-in user has joined the main contest</li>
 *   <li>isLoggedIn - true if user is authenticated</li>
 * </ul>
 *
 * <p>Navbar label logic:
 * <ul>
 *   <li>Not logged in → "Predictions" (links to /predictions/user/guest)</li>
 *   <li>Logged in → "My Predictions" (links to /predictions/user/me)</li>
 * </ul>
 */
@ControllerAdvice
@RequiredArgsConstructor
public class NavbarControllerAdvice {

    private final ContestRepo contestRepo;
    private final SeasonRepo seasonRepo;
    private final CompetitionDefaults competitionDefaults;
    private final UserRepo userRepo;

    @ModelAttribute("isLoggedIn")
    public boolean isLoggedIn(Principal principal) {
        return principal != null;
    }

    @ModelAttribute("hasContestEntry")
    public boolean hasContestEntry(Principal principal) {
        if (principal == null) {
            return false;
        }

        UUID userId = resolveUserId(principal);
        if (userId == null) {
            return false;
        }

        UUID mainContestId = getActiveSeason().getMainContestId();
        if (mainContestId == null) {
            return false;
        }

        return contestRepo.existsByUserAndContest(userId, mainContestId);
    }

    @ModelAttribute("predictionsNavLabel")
    public String predictionsNavLabel(Principal principal) {
        return principal != null ? "My Predictions" : "Predictions";
    }

    @ModelAttribute("predictionsNavLink")
    public String predictionsNavLink(Principal principal) {
        if (principal == null) {
            return "/predictions/user/guest";
        }
        return "/predictions/user/me";
    }

    @ModelAttribute("userDisplayName")
    public String userDisplayName(Principal principal) {
        if (principal == null) {
            return null;
        }

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof WebUserDetails details) {
            return details.getDisplayName();
        }

        return resolveUser(principal).map(User::getDisplayName).orElse(null);
    }

    private UUID resolveUserId(Principal principal) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof WebUserDetails details) {
            return details.getUserId();
        }

        return resolveUser(principal).map(User::getId).orElse(null);
    }

    private Optional<User> resolveUser(Principal principal) {
        if (principal == null || principal.getName() == null || principal.getName().isBlank()) {
            return Optional.empty();
        }

        try {
            Email email = Email.create(principal.getName());
            return userRepo.findByEmail(email);
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    private Season getActiveSeason() {
        return seasonRepo.findMostRecentSeason(competitionDefaults.defaultCompetitionSlug())
                .orElseThrow(() -> new IllegalStateException("No active season available"));
    }
}
