package com.ligitabl.api.web.auth;

import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.ligitabl.api.auth.impersonation.CurrentUserFacade;
import com.ligitabl.api.auth.impersonation.ImpersonationGuard;
import com.ligitabl.api.auth.impersonation.UserSummary;
import com.ligitabl.api.auth.oauth2.OAuth2AuthenticationSuccessHandler;
import com.ligitabl.api.auth.security.WebUserDetails;
import com.ligitabl.api.usecase.account.googlelink.UnlinkGoogleError;
import com.ligitabl.api.usecase.account.googlelink.UnlinkGoogleUseCase;
import com.ligitabl.model.domain.User;
import com.ligitabl.model.repo.UserRepo;

import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@ConditionalOnWebApplication
@Controller
@RequestMapping("/profile")
@RequiredArgsConstructor
@Slf4j
public class ConnectedAccountsController {

    private final UserRepo userRepo;
    private final UnlinkGoogleUseCase unlinkGoogleUseCase;
    private final ImpersonationGuard impersonationGuard;
    private final CurrentUserFacade currentUserFacade;

    @GetMapping("/connected-accounts")
    public String connectedAccounts(
            Model model, @AuthenticationPrincipal WebUserDetails userDetails, HttpSession session) {
        User user = currentUser(userDetails);
        if (user == null) {
            return "redirect:/auth/login";
        }

        boolean googleLinked = user.getGoogleId() != null && !user.getGoogleId().isBlank();
        boolean canUnlinkGoogle = googleLinked && user.getPassword() != null;

        model.addAttribute("pageTitle", "Connected Accounts");
        model.addAttribute("googleLinked", googleLinked);
        model.addAttribute("canUnlinkGoogle", canUnlinkGoogle);
        model.addAttribute("accountEmail", user.getEmail().value());
        model.addAttribute("googleEmail", googleLinked ? user.getEmail().value() : null);
        model.addAttribute("googleSubject", user.getGoogleId());

        Object linkingMessage =
                session.getAttribute(OAuth2AuthenticationSuccessHandler.LINKING_FEEDBACK_MESSAGE_SESSION_KEY);
        Object linkingMessageType =
                session.getAttribute(OAuth2AuthenticationSuccessHandler.LINKING_FEEDBACK_TYPE_SESSION_KEY);
        if (linkingMessage instanceof String message && !message.isBlank()) {
            model.addAttribute("message", message);
            if (linkingMessageType instanceof String messageType && !messageType.isBlank()) {
                model.addAttribute("messageType", messageType);
            }
            session.removeAttribute(OAuth2AuthenticationSuccessHandler.LINKING_FEEDBACK_MESSAGE_SESSION_KEY);
            session.removeAttribute(OAuth2AuthenticationSuccessHandler.LINKING_FEEDBACK_TYPE_SESSION_KEY);
        }

        return "profile/connected-accounts";
    }

    @GetMapping("/link-google")
    public String startGoogleLinking(
            @AuthenticationPrincipal WebUserDetails userDetails,
            HttpSession session,
            RedirectAttributes redirectAttributes) {
        User user = currentUser(userDetails);
        if (user == null) {
            return "redirect:/auth/login";
        }

        session.setAttribute(OAuth2AuthenticationSuccessHandler.LINKING_MODE_SESSION_KEY, true);
        session.setAttribute(OAuth2AuthenticationSuccessHandler.LINKING_USER_ID_SESSION_KEY, user.getId());

        log.info("[START_GOOGLE_LINKING] userId={}", user.getId());
        return "redirect:/oauth2/authorization/google";
    }

    @PostMapping("/unlink-google")
    public String unlinkGoogle(
            @AuthenticationPrincipal WebUserDetails userDetails, RedirectAttributes redirectAttributes) {
        impersonationGuard.assertNotBlocked();
        User user = currentUser(userDetails);
        if (user == null) {
            return "redirect:/auth/login";
        }

        return unlinkGoogleUseCase
                .execute(user.getId())
                .fold(
                        error -> switch (error) {
                            case UnlinkGoogleError.NotLinked __ -> {
                                flash(redirectAttributes, "Google account is not linked.", "info");
                                yield "redirect:/profile/connected-accounts";
                            }
                            case UnlinkGoogleError.PasswordRequired __ -> {
                                flash(
                                        redirectAttributes,
                                        "Set a password before unlinking Google so you can still sign in.",
                                        "error");
                                yield "redirect:/profile/connected-accounts";
                            }
                            case UnlinkGoogleError.UserNotFound __ -> "redirect:/auth/login";
                        },
                        updatedUser -> {
                            flash(redirectAttributes, "Google account unlinked successfully.", "success");
                            return "redirect:/profile/connected-accounts";
                        });
    }

    private static void flash(RedirectAttributes redirectAttributes, String message, String messageType) {
        redirectAttributes.addFlashAttribute("message", message);
        redirectAttributes.addFlashAttribute("messageType", messageType);
    }

    private User currentUser(WebUserDetails userDetails) {
        if (userDetails == null) {
            return null;
        }

        // Shows the effective user's account state while an admin is impersonating
        var userId = currentUserFacade.isImpersonating()
                ? currentUserFacade.getEffectiveUser().map(UserSummary::id).orElse(userDetails.getUserId())
                : userDetails.getUserId();
        return userRepo.findById(userId).orElse(null);
    }
}
