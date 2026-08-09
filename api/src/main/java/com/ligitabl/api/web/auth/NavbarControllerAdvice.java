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

import com.ligitabl.api.auth.impersonation.CurrentUserFacade;
import com.ligitabl.api.auth.impersonation.UserSummary;
import com.ligitabl.api.auth.security.WebUserDetails;
import com.ligitabl.api.config.CompetitionDefaults;
import com.ligitabl.api.rest.finaltable.shared.FinalTableSupport;
import com.ligitabl.model.auth.Email;
import com.ligitabl.model.domain.Season;
import com.ligitabl.model.domain.User;
import com.ligitabl.model.repo.ContestRepo;
import com.ligitabl.model.repo.FinalTablePredictionRepo;
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
 *   <li>showFinalTableNav - whether to offer the Final Table link (see below)</li>
 *   <li>finalTableEntryOpen - whether the Final Table still accepts entries (see below)</li>
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
    private final CurrentUserFacade currentUserFacade;
    private final FinalTableSupport finalTableSupport;
    private final FinalTablePredictionRepo predictionRepo;

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

    /**
     * Whether to offer the Final Table Predictor in the navbar.
     *
     * <p>Two ways to qualify, and a player needs only one:
     *
     * <ul>
     *   <li><b>Entry is still open</b> — anyone can go and make a table, so the link is an
     *       invitation. This is the guest on-ramp, and it covers signed-in players too.
     *   <li><b>They already have a table</b> — it is theirs to revisit whether it is locked or
     *       scored, and a locked table is the whole point of the game.
     * </ul>
     *
     * <p>⚠️ Neither alone is sufficient, which an earlier version got wrong by returning true for
     * every signed-in user. A player who never entered, arriving after the lock, was offered a link
     * to a game they cannot join and have nothing in — a dead end, and exactly what the guest rule
     * already avoided. The two cases are the same problem: do not advertise a closed game to
     * someone with no stake in it.
     *
     * <p>Falls back to hiding the link if the season can't be resolved: a nav item that 503s is
     * worse than an absent one.
     */
    @ModelAttribute("showFinalTableNav")
    public boolean showFinalTableNav(Principal principal) {
        try {
            Season season = getActiveSeason();
            if (finalTableSupport.isEntryOpen(season)) {
                return true;
            }
            // Closed: only worth linking if they have something there. Checked last so the common
            // open-season case never costs a query.
            if (!isAuthenticatedUser(currentAuthentication()) || principal == null) {
                return false;
            }
            UUID userId = resolveUserId(principal);
            return userId != null
                    && predictionRepo
                            .findByUserAndSeason(userId, season.getId())
                            .isPresent();
        } catch (RuntimeException e) {
            return false;
        }
    }

    /**
     * Whether the Final Table still accepts entries.
     *
     * <p>Distinct from {@link #showFinalTableNav()}, which is true for any signed-in player because
     * a locked table is still worth visiting. This one is for prompts that invite someone to go and
     * <em>make</em> one — pointing a player at a game that closed at gameweek 1 is worse than
     * staying quiet. Same defensive fallback: an unresolvable season means no prompt.
     */
    @ModelAttribute("finalTableEntryOpen")
    public boolean finalTableEntryOpen() {
        try {
            return finalTableSupport.isEntryOpen(getActiveSeason());
        } catch (RuntimeException e) {
            return false;
        }
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

        // Navbar identity is data, not a permission gate — show the effective user's name
        // while impersonating (isAdmin above intentionally stays on the real principal).
        if (currentUserFacade.isImpersonating()) {
            return currentUserFacade
                    .getEffectiveUser()
                    .map(u -> u.displayName() != null && !u.displayName().isBlank() ? u.displayName() : u.email())
                    .orElse("User");
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

        if (currentUserFacade.isImpersonating()) {
            return currentUserFacade.getEffectiveUser().map(UserSummary::email).orElse(null);
        }

        Authentication authentication = currentAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof WebUserDetails details) {
            return details.getEmail();
        }

        return resolveUser(principal).map(u -> u.getEmail().value()).orElse(null);
    }

    @ModelAttribute("emailUnverified")
    public boolean emailUnverified(Principal principal) {
        if (!isAuthenticatedUser(currentAuthentication()) || principal == null) {
            return false;
        }

        // While impersonating, resend is guard-blocked and the impersonation banner
        // already occupies the slot — don't show a nudge the admin can't act on.
        if (currentUserFacade.isImpersonating()) {
            return false;
        }

        UUID userId = resolveUserId(principal);
        if (userId == null) {
            return false;
        }

        return userRepo.findById(userId).map(u -> !u.isEmailVerified()).orElse(false);
    }

    @ModelAttribute("isImpersonating")
    public boolean isImpersonating() {
        return currentUserFacade.isImpersonating();
    }

    @ModelAttribute("effectiveUserEmail")
    public String effectiveUserEmail() {
        return currentUserFacade.getEffectiveUser().map(UserSummary::email).orElse(null);
    }

    @ModelAttribute("originalUserEmail")
    public String originalUserEmail() {
        return currentUserFacade.getOriginalUser().map(UserSummary::email).orElse(null);
    }

    private UUID resolveUserId(Principal principal) {
        Authentication authentication = currentAuthentication();
        if (!isAuthenticatedUser(authentication)) {
            return null;
        }

        // Navbar chips (e.g. hasContestEntry) follow the effective user while impersonating
        if (currentUserFacade.isImpersonating()) {
            UUID effectiveId =
                    currentUserFacade.getEffectiveUser().map(UserSummary::id).orElse(null);
            if (effectiveId != null) {
                return effectiveId;
            }
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
