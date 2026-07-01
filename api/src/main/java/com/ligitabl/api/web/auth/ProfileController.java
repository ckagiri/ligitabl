package com.ligitabl.api.web.auth;

import java.util.List;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.ligitabl.api.auth.security.WebUserDetails;
import com.ligitabl.api.rest.contest.shared.ContestRankResolver;
import com.ligitabl.model.auth.Password;
import com.ligitabl.model.domain.User;
import com.ligitabl.model.domain.service.PasswordHasher;
import com.ligitabl.model.repo.ContestRepo;
import com.ligitabl.model.repo.EntryRepo;
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
@RequestMapping("/settings")
@RequiredArgsConstructor
@Slf4j
public class ProfileController {

    private final UserRepo userRepo;
    private final PasswordHasher passwordHasher;
    private static final int PAGE_SIZE = 10;

    private final EntryRepo entryRepo;
    private final ContestRepo contestRepo;
    private final ContestRankResolver contestRankResolver;

    @GetMapping("/profile")
    public String profile(
            @AuthenticationPrincipal WebUserDetails userDetails,
            @RequestParam(defaultValue = "1") int activePage,
            @RequestParam(defaultValue = "1") int pastPage,
            Model model) {
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

        buildContestLists(user.getId(), activePage, pastPage, model);

        return "settings/profile";
    }

    @InitBinder("profileForm")
    public void initProfileFormBinder(WebDataBinder binder) {
        binder.registerCustomEditor(String.class, new StringTrimmerEditor(false));
    }

    @PostMapping("/profile")
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
            buildContestLists(user.getId(), 1, 1, model);
            return "settings/profile";
        }

        User updatedUser = user.withDisplayName(form.getDisplayName());
        userRepo.update(updatedUser);
        refreshSessionAuthentication(updatedUser, session);

        log.info("[PROFILE_UPDATED] userId={} newDisplayName={}", updatedUser.getId(), updatedUser.getDisplayName());
        redirectAttributes.addFlashAttribute("message", "Profile updated successfully");
        redirectAttributes.addFlashAttribute("messageType", "success");

        return "redirect:/settings/profile";
    }

    @GetMapping("/set-password")
    public String showSetPasswordForm(@AuthenticationPrincipal WebUserDetails userDetails, Model model) {
        User user = currentUser(userDetails);
        if (user == null) {
            return "redirect:/auth/login";
        }
        if (user.getPassword() != null) {
            return "redirect:/settings/profile";
        }
        model.addAttribute("pageTitle", "Set Password");
        model.addAttribute("setPasswordForm", new SetPasswordForm());
        return "settings/set-password";
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
            return "redirect:/settings/profile";
        }

        if (!form.getNewPassword().equals(form.getConfirmPassword())) {
            result.rejectValue("confirmPassword", "password.mismatch", "Passwords do not match");
        }

        if (result.hasErrors()) {
            model.addAttribute("pageTitle", "Set Password");
            return "settings/set-password";
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
            return "redirect:/settings/connected-accounts";
        } catch (IllegalArgumentException e) {
            result.rejectValue("newPassword", "password.invalid", "Password does not meet requirements");
            model.addAttribute("pageTitle", "Set Password");
            return "settings/set-password";
        }
    }

    private void buildContestLists(UUID userId, int activePage, int pastPage, Model model) {
        int activeTotal = contestRepo.countContestsByUserId(userId, false);
        int pastTotal = contestRepo.countContestsByUserId(userId, true);

        int activeOffset = (Math.max(1, activePage) - 1) * PAGE_SIZE;
        int pastOffset = (Math.max(1, pastPage) - 1) * PAGE_SIZE;

        List<ContestSummary> activeContests = contestRepo
                .findContestsByUserId(userId, false, PAGE_SIZE, activeOffset)
                .stream()
                .map(v -> toSummary(v, userId))
                .toList();

        List<ContestSummary> pastContests = contestRepo
                .findContestsByUserId(userId, true, PAGE_SIZE, pastOffset)
                .stream()
                .map(v -> toSummary(v, userId))
                .toList();

        model.addAttribute("activeContests", activeContests);
        model.addAttribute("activeTotal", activeTotal);
        model.addAttribute("activePage", activePage);
        model.addAttribute("activePages", (int) Math.ceil((double) activeTotal / PAGE_SIZE));
        model.addAttribute("activeFrom", activeTotal == 0 ? 0 : activeOffset + 1);
        model.addAttribute("activeTo", Math.min(activeOffset + PAGE_SIZE, activeTotal));

        model.addAttribute("pastContests", pastContests);
        model.addAttribute("pastTotal", pastTotal);
        model.addAttribute("pastPage", pastPage);
        model.addAttribute("pastPages", (int) Math.ceil((double) pastTotal / PAGE_SIZE));
        model.addAttribute("pastFrom", pastTotal == 0 ? 0 : pastOffset + 1);
        model.addAttribute("pastTo", Math.min(pastOffset + PAGE_SIZE, pastTotal));
    }

    private ContestSummary toSummary(ContestRepo.UserContestView view, UUID userId) {
        int memberCount = entryRepo.countActiveByContestId(view.contestId());
        Integer rank = resolveRank(view, userId);
        String link = "/contests/" + view.contestId() + (view.isPrivate() ? "" : "?segment=overall");
        return new ContestSummary(view.contestName(), view.seasonName(), memberCount, rank, link);
    }

    private Integer resolveRank(ContestRepo.UserContestView view, UUID userId) {
        try {
            return contestRankResolver
                    .resolve(
                            view.contestId(),
                            view.seasonId(),
                            view.fromRoundPosition(),
                            view.toRoundPosition(),
                            userId)
                    .position();
        } catch (Exception e) {
            log.warn("Could not resolve rank for user {} in contest {}: {}", userId, view.contestId(), e.getMessage());
            return null;
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

    private User currentUser(WebUserDetails userDetails) {
        if (userDetails == null) {
            return null;
        }

        UUID userId = userDetails.getUserId();
        return userRepo.findById(userId).orElse(null);
    }

    public record ContestSummary(String contestName, String seasonName, int memberCount, Integer rank, String link) {}

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
