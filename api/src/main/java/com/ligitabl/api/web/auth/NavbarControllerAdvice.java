package com.ligitabl.api.web.auth;

import java.security.Principal;
import java.util.Optional;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

import com.ligitabl.api.auth.security.WebUserDetails;
import com.ligitabl.api.config.CompetitionDefaults;
import com.ligitabl.model.auth.Email;
import com.ligitabl.model.domain.Season;
import com.ligitabl.model.domain.User;
import com.ligitabl.model.repo.ContestRepo;
import com.ligitabl.model.repo.SeasonRepo;
import com.ligitabl.model.repo.UserRepo;

import lombok.RequiredArgsConstructor;

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
 *   <li>Not logged in → "My Table" (links to /my-table/guest)</li>
 *   <li>Logged in → "My Table" (links to /my-table)</li>
 * </ul>
 */
@ControllerAdvice
@ConditionalOnBean({ContestRepo.class, SeasonRepo.class, UserRepo.class})
@RequiredArgsConstructor
public class NavbarControllerAdvice {

    private final ContestRepo contestRepo;
    private final SeasonRepo seasonRepo;
    private final CompetitionDefaults competitionDefaults;
    private final UserRepo userRepo;

    @Value("${umami.website-id:}")
    private String umamiWebsiteId;

    @ModelAttribute("umamiWebsiteId")
    public String umamiWebsiteId() {
        return umamiWebsiteId;
    }

    @ModelAttribute("isLoggedIn")
    public boolean isLoggedIn(Principal principal) {
        return isAuthenticatedUser(currentAuthentication());
    }

    @ModelAttribute("isAdmin")
    public boolean isAdmin(Principal principal) {
        Authentication authentication = currentAuthentication();
        if (!isAuthenticatedUser(authentication)) {
            return false;
        }
        return authentication.getAuthorities().stream()
                .anyMatch(authority -> "ROLE_ADMIN".equals(authority.getAuthority()));
    }

    @ModelAttribute("hasContestEntry")
    public boolean hasContestEntry(Principal principal) {
        if (!isAuthenticatedUser(currentAuthentication()) || principal == null) {
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
    public String predictionsNavLabel() {
        return "My Table";
    }

    @ModelAttribute("predictionsNavLink")
    public String predictionsNavLink(Principal principal) {
        if (!isAuthenticatedUser(currentAuthentication()) || principal == null) {
            return "/my-table/guest";
        }
        return "/my-table";
    }

    @ModelAttribute("userDisplayName")
    public String userDisplayName(Principal principal) {
        if (!isAuthenticatedUser(currentAuthentication()) || principal == null) {
            return null;
        }

        Authentication authentication = currentAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof WebUserDetails details) {
            return details.getDisplayName();
        }

        return resolveUser(principal).map(User::getDisplayName).orElse("User");
    }

    @ModelAttribute("userEmail")
    public String userEmail(Principal principal) {
        if (!isAuthenticatedUser(currentAuthentication()) || principal == null) {
            return null;
        }

        Authentication authentication = currentAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof WebUserDetails details) {
            return details.getEmail();
        }

        return resolveUser(principal).map(u -> u.getEmail().value()).orElse(null);
    }

    private UUID resolveUserId(Principal principal) {
        Authentication authentication = currentAuthentication();
        if (!isAuthenticatedUser(authentication)) {
            return null;
        }
        if (authentication != null && authentication.getPrincipal() instanceof WebUserDetails details) {
            return details.getUserId();
        }

        return resolveUser(principal).map(User::getId).orElse(null);
    }

    private Optional<User> resolveUser(Principal principal) {
        if (principal == null
                || principal.getName() == null
                || principal.getName().isBlank()) {
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
        return seasonRepo
                .findActiveSeason(competitionDefaults.defaultCompetitionSlug())
                .orElseThrow(() -> new IllegalStateException("No active season available"));
    }

    private Authentication currentAuthentication() {
        return SecurityContextHolder.getContext().getAuthentication();
    }

    private boolean isAuthenticatedUser(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return false;
        }
        if (authentication instanceof AnonymousAuthenticationToken) {
            return false;
        }
        Object principal = authentication.getPrincipal();
        return !(principal instanceof String value && "anonymousUser".equalsIgnoreCase(value));
    }
}
