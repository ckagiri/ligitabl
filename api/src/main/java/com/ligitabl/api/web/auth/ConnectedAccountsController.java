package com.ligitabl.api.web.auth;

import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.ligitabl.api.auth.oauth2.OAuth2AuthenticationSuccessHandler;
import com.ligitabl.api.auth.security.WebUserDetails;
import com.ligitabl.model.domain.User;
import com.ligitabl.model.repo.UserRepo;

import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@ConditionalOnWebApplication
@Controller
@RequestMapping("/settings")
@RequiredArgsConstructor
@Slf4j
public class ConnectedAccountsController {

    private final UserRepo userRepo;

    @GetMapping("/connected-accounts")
    public String connectedAccounts(Model model, @AuthenticationPrincipal WebUserDetails userDetails) {
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

        return "settings/connected-accounts";
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
        User user = currentUser(userDetails);
        if (user == null) {
            return "redirect:/auth/login";
        }

        boolean googleLinked = user.getGoogleId() != null && !user.getGoogleId().isBlank();
        if (!googleLinked) {
            redirectAttributes.addFlashAttribute("message", "Google account is not linked.");
            redirectAttributes.addFlashAttribute("messageType", "info");
            return "redirect:/settings/connected-accounts";
        }

        if (user.getPassword() == null) {
            redirectAttributes.addFlashAttribute(
                    "message", "Set a password before unlinking Google so you can still sign in.");
            redirectAttributes.addFlashAttribute("messageType", "error");
            return "redirect:/settings/connected-accounts";
        }

        User updatedUser = new User(
                user.getId(),
                user.getPublicId(),
                user.getEmail(),
                user.getDisplayName(),
                user.getPassword(),
                user.getRoles(),
                user.isEmailVerified(),
                null);

        userRepo.update(updatedUser);

        log.info("[GOOGLE_UNLINKED] userId={}", user.getId());
        redirectAttributes.addFlashAttribute("message", "Google account unlinked successfully.");
        redirectAttributes.addFlashAttribute("messageType", "success");
        return "redirect:/settings/connected-accounts";
    }

    private User currentUser(WebUserDetails userDetails) {
        if (userDetails == null) {
            return null;
        }

        return userRepo.findById(userDetails.getUserId()).orElse(null);
    }
}
