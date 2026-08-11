package com.ligitabl.api.web.auth;

import java.security.Principal;
import java.time.Clock;
import java.time.Duration;
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
import com.ligitabl.model.repo.EmailVerificationTokenRepo;
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
    private final EmailVerificationTokenRepo emailVerificationTokenRepo;
    private final Clock clock;

    @Value("${umami.website-id:}")
    private String umamiWebsiteId;

    @Value("${ligitabl.security.email-verification-resend-quiet-minutes:30}")
    private int resendQuietMinutes;

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

    /**
     * Whether the verify-email banner should offer its "Resend email" action.
     *
     * <p>Suppressed while the most recent verification email is still fresh — right after signup the
     * mail is already in flight, so resend invites a duplicate, and inside
     * {@code email-verification-resend-cooldown-minutes} the server would reject the attempt with
     * {@code ResendTooSoon} anyway. The quiet window is deliberately its own setting, and longer than
     * that cooldown: the cooldown is anti-abuse throttling, this is "don't ask yet".
     *
     * <p>Only consulted when {@code emailUnverified} is already true, so the extra token read costs
     * nothing for verified users — the common case.
     */
    @ModelAttribute("verificationResendAvailable")
    public boolean verificationResendAvailable(Principal principal) {
        if (!isAuthenticatedUser(currentAuthentication()) || principal == null) {
            return false;
        }

        UUID userId = resolveUserId(principal);
        if (userId == null) {
            return false;
        }

        try {
            return emailVerificationTokenRepo
                    .findLatestForUser(userId)
                    .map(token -> clock.instant()
                            .isAfter(token.getCreatedAt().plus(Duration.ofMinutes(resendQuietMinutes))))
                    // No token on record means nothing was sent to duplicate — let them ask for one.
                    .orElse(true);
        } catch (RuntimeException e) {
            // A banner button is not worth failing a page render over; offering resend is the
            // safe fallback, since the server still throttles an over-eager click.
            return true;
        }
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
