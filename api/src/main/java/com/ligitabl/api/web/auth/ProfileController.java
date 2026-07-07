package com.ligitabl.api.web.auth;

import java.util.UUID;

import org.springframework.beans.propertyeditors.StringTrimmerEditor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.InitBinder;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.ligitabl.api.auth.security.WebUserDetails;
import com.ligitabl.api.config.CompetitionDefaults;
import com.ligitabl.api.web.shared.season.SeasonPredictionSupport;
import com.ligitabl.model.auth.Password;
import com.ligitabl.model.auth.Role;
import com.ligitabl.model.domain.User;
import com.ligitabl.model.domain.service.PasswordHasher;
import com.ligitabl.model.repo.UserRepo;

import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@ConditionalOnWebApplication
@Controller
@RequestMapping("/profile")
@RequiredArgsConstructor
@Slf4j
public class ProfileController {

    private final UserRepo userRepo;
    private final PasswordHasher passwordHasher;
    private final SeasonPredictionSupport seasonPredictionSupport;
    private final CompetitionDefaults competitionDefaults;

    @GetMapping("/settings")
    public String profile(@AuthenticationPrincipal WebUserDetails userDetails, Model model) {
        User user = currentUser(userDetails);
        if (user == null) {
            return "redirect:/auth/login";
        }

        ProfileForm form = new ProfileForm();
        form.setDisplayName(user.getDisplayName());
        form.setEmail(user.getEmail().value());
        model.addAttribute("profileForm", form);

        model.addAttribute("pageTitle", "Profile Settings");
        model.addAttribute("user", user);
        model.addAttribute("showRoles", hasNonDefaultRoles(user));
        populateShareModel(user, model);

        return "profile/settings";
    }

    @InitBinder("profileForm")
    public void initProfileFormBinder(WebDataBinder binder) {
        binder.registerCustomEditor(String.class, new StringTrimmerEditor(false));
    }

    @PostMapping("/settings")
    public String updateProfile(
            @AuthenticationPrincipal WebUserDetails userDetails,
            @Valid @ModelAttribute("profileForm") ProfileForm form,
            BindingResult result,
            HttpSession session,
            RedirectAttributes redirectAttributes,
            Model model) {
        User user = currentUser(userDetails);
        if (user == null) {
            return "redirect:/auth/login";
        }

        form.setEmail(user.getEmail().value());

        if (result.hasErrors()) {
            model.addAttribute("pageTitle", "Profile Settings");
            model.addAttribute("user", user);
            model.addAttribute("showRoles", hasNonDefaultRoles(user));
            populateShareModel(user, model);
            return "profile/settings";
        }

        User updatedUser = user.withDisplayName(form.getDisplayName());
        userRepo.update(updatedUser);
        refreshSessionAuthentication(updatedUser, session);

        log.info("[PROFILE_UPDATED] userId={} newDisplayName={}", updatedUser.getId(), updatedUser.getDisplayName());
        redirectAttributes.addFlashAttribute("message", "Profile updated successfully");
        redirectAttributes.addFlashAttribute("messageType", "success");

        return "redirect:/profile/settings";
    }

    @GetMapping("/set-password")
    public String showSetPasswordForm(@AuthenticationPrincipal WebUserDetails userDetails, Model model) {
        User user = currentUser(userDetails);
        if (user == null) {
            return "redirect:/auth/login";
        }
        if (user.getPassword() != null) {
            return "redirect:/profile/settings";
        }
        model.addAttribute("pageTitle", "Set Password");
        model.addAttribute("setPasswordForm", new SetPasswordForm());
        return "profile/set-password";
    }

    @PostMapping("/set-password")
    public String setPassword(
            @AuthenticationPrincipal WebUserDetails userDetails,
            @Valid @ModelAttribute("setPasswordForm") SetPasswordForm form,
            BindingResult result,
            HttpSession session,
            RedirectAttributes redirectAttributes,
            Model model) {
        User user = currentUser(userDetails);
        if (user == null) {
            return "redirect:/auth/login";
        }
        if (user.getPassword() != null) {
            redirectAttributes.addFlashAttribute("message", "A password is already set for this account.");
            redirectAttributes.addFlashAttribute("messageType", "info");
            return "redirect:/profile/settings";
        }

        if (!form.getNewPassword().equals(form.getConfirmPassword())) {
            result.rejectValue("confirmPassword", "password.mismatch", "Passwords do not match");
        }

        if (result.hasErrors()) {
            model.addAttribute("pageTitle", "Set Password");
            return "profile/set-password";
        }

        try {
            Password.Hashed hashed = passwordHasher.hash(Password.Plaintext.create(form.getNewPassword()));
            User updatedUser = user.withPassword(hashed);
            userRepo.update(updatedUser);
            refreshSessionAuthentication(updatedUser, session);

            log.info("[PASSWORD_SET] userId={}", updatedUser.getId());
            redirectAttributes.addFlashAttribute(
                    "message", "Password set successfully. You can now sign in with email and password.");
            redirectAttributes.addFlashAttribute("messageType", "success");
            return "redirect:/profile/connected-accounts";
        } catch (IllegalArgumentException e) {
            result.rejectValue("newPassword", "password.invalid", "Password does not meet requirements");
            model.addAttribute("pageTitle", "Set Password");
            return "profile/set-password";
        }
    }

    private void refreshSessionAuthentication(User user, HttpSession session) {
        var authorities = user.getRoles().stream()
                .map(role -> new SimpleGrantedAuthority("ROLE_" + role.name()))
                .toList();

        WebUserDetails updatedDetails = new WebUserDetails(
                user.getId(),
                user.getPublicId().value(),
                user.getEmail().value(),
                user.getDisplayName(),
                user.getPassword() == null ? "" : user.getPassword().value(),
                authorities);

        var authentication = new UsernamePasswordAuthenticationToken(updatedDetails, null, authorities);
        SecurityContextHolder.getContext().setAuthentication(authentication);
        session.setAttribute(
                HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY, SecurityContextHolder.getContext());
    }

    /**
     * Populates the "share your prediction" section — only visible when the user actually has
     * something to share: pre-season registration (initialRankings) or an in-play prediction
     * (currentRankings).
     */
    private void populateShareModel(User user, Model model) {
        var shareData = seasonPredictionSupport.buildShareData(user, competitionDefaults.defaultCompetitionSlug());
        model.addAttribute("showShareSection", shareData.visible());
        model.addAttribute("shareUrl", shareData.shareUrl());
        model.addAttribute("shareText", shareData.shareText());
    }

    private boolean hasNonDefaultRoles(User user) {
        return !(user.getRoles().size() == 1 && user.getRoles().contains(Role.PLAYER));
    }

    private User currentUser(WebUserDetails userDetails) {
        if (userDetails == null) {
            return null;
        }

        UUID userId = userDetails.getUserId();
        return userRepo.findById(userId).orElse(null);
    }

    @Data
    public static class ProfileForm {
        @NotBlank(message = "Display name is required")
        @Size(min = 3, max = 30, message = "Display name must be between 3 and 30 characters")
        private String displayName;

        private String email;
    }

    @Data
    public static class SetPasswordForm {
        @NotBlank(message = "Password is required")
        @Size(min = 8, message = "Password must be at least 8 characters")
        private String newPassword;

        @NotBlank(message = "Please confirm your password")
        private String confirmPassword;
    }
}
