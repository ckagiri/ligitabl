package com.ligitabl.api.web.auth;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.springframework.beans.propertyeditors.StringTrimmerEditor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
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
import com.ligitabl.model.auth.Password;
import com.ligitabl.model.domain.Contest;
import com.ligitabl.model.domain.LeaderboardResponse;
import com.ligitabl.model.domain.Season;
import com.ligitabl.model.domain.User;
import com.ligitabl.model.domain.service.PasswordHasher;
import com.ligitabl.model.repo.ContestRepo;
import com.ligitabl.model.repo.EntryRepo;
import com.ligitabl.model.repo.LeaderboardRepo;
import com.ligitabl.model.repo.SeasonRepo;
import com.ligitabl.model.repo.UserRepo;

import jakarta.servlet.http.HttpSession;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
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
    private final EntryRepo entryRepo;
    private final ContestRepo contestRepo;
    private final SeasonRepo seasonRepo;
    private final LeaderboardRepo leaderboardRepo;

    @GetMapping("/profile")
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

        buildContestLists(user.getId(), model);

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
            buildContestLists(user.getId(), model);
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

    private void buildContestLists(UUID userId, Model model) {
        List<ContestSummary> activeContests = new ArrayList<>();
        List<ContestSummary> pastContests = new ArrayList<>();

        entryRepo.findByUserId(userId).forEach(entry -> {
            contestRepo.findById(entry.getContestId()).ifPresent(contest -> {
                if (!contest.isMain()) {
                    return;
                }
                seasonRepo.findById(contest.getSeasonId()).ifPresent(season -> {
                    if (season.getMaxRounds() > 0 && contest.getToRoundPosition() != season.getMaxRounds()) {
                        return;
                    }
                    int memberCount = entryRepo.countActiveByContestId(contest.getId());
                    Integer rank = resolveRank(contest, season, userId);
                    ContestSummary summary = new ContestSummary(
                            contest.getName(), season.getName(), memberCount, rank);
                    if (season.isCompleted()) {
                        pastContests.add(summary);
                    } else {
                        activeContests.add(summary);
                    }
                });
            });
        });

        model.addAttribute("activeContests", activeContests);
        model.addAttribute("pastContests", pastContests);
    }

    private Integer resolveRank(Contest contest, Season season, UUID userId) {
        try {
            LeaderboardResponse leaderboard = leaderboardRepo.computeLeaderboard(
                    contest.getId(), season.getId(), 1, season.getMaxRounds(), userId, 0, 1, true);
            return leaderboard.userEntry() != null ? leaderboard.userEntry().position() : null;
        } catch (Exception e) {
            log.warn("Could not resolve rank for user {} in contest {}: {}", userId, contest.getId(), e.getMessage());
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

    public record ContestSummary(String contestName, String seasonName, int memberCount, Integer rank) {}

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
