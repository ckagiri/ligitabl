package com.ligitabl.api.web.auth;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.authentication.RememberMeServices;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.savedrequest.SavedRequest;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.ligitabl.api.auth.security.WebUserDetails;
import com.ligitabl.api.config.CompetitionDefaults;
import com.ligitabl.api.rest.auth.register.RegisterCommand;
import com.ligitabl.api.rest.auth.register.RegisterResult;
import com.ligitabl.api.rest.auth.register.RegisterUseCase;
import com.ligitabl.api.shared.Either;
import com.ligitabl.api.shared.errors.UseCaseError;
import com.ligitabl.model.auth.Email;
import com.ligitabl.model.auth.Password;
import com.ligitabl.model.auth.Role;
import com.ligitabl.model.domain.Season;
import com.ligitabl.model.domain.User;
import com.ligitabl.model.domain.service.PasswordHasher;
import com.ligitabl.model.repo.ContestRepo;
import com.ligitabl.model.repo.SeasonRepo;
import com.ligitabl.model.repo.UserRepo;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@ConditionalOnWebApplication
@Controller
@RequestMapping("/auth")
@RequiredArgsConstructor
@Slf4j
public class AuthController {
    private final ContestRepo contestRepo;
    private final RegisterUseCase registerUseCase;
    private final UserRepo userRepo;
    private final PasswordHasher passwordHasher;
    private final SeasonRepo seasonRepo;
    private final CompetitionDefaults competitionDefaults;
    private final RememberMeServices rememberMeServices;
    private final RequestPasswordResetUseCase requestPasswordResetUseCase;
    private final ResetPasswordUseCase resetPasswordUseCase;

    @GetMapping("/register")
    public String showRegisterForm(Model model, HttpServletRequest request) {
        request.getSession(true);
        model.addAttribute("pageTitle", "Register");
        model.addAttribute("registerForm", new RegisterForm());
        return "auth/register";
    }

    @PostMapping("/register")
    public String register(
            @Valid @ModelAttribute RegisterForm form,
            BindingResult bindingResult,
            RedirectAttributes redirectAttributes,
            Model model,
            HttpServletRequest request) {
        if (bindingResult.hasErrors()) {
            model.addAttribute("pageTitle", "Register");
            return "auth/register";
        }

        if (!form.getPassword().equals(form.getConfirmPassword())) {
            bindingResult.rejectValue("confirmPassword", "password.mismatch", "Passwords do not match");
            model.addAttribute("pageTitle", "Register");
            return "auth/register";
        }

        try {
            Email email = Email.create(form.getEmail());
            Password.Plaintext password = Password.Plaintext.create(form.getPassword());

            var registerCommand = new RegisterCommand(email, form.getDisplayName(), password);

            Either<UseCaseError, RegisterResult> registerResult = registerUseCase.execute(registerCommand);

            return registerResult.fold(
                    error -> {
                        model.addAttribute("error", error.getMessage());
                        model.addAttribute("email", form.getEmail());
                        model.addAttribute("displayName", form.getDisplayName());
                        return "auth/register";
                    },
                    result -> {
                        User user = userRepo.findByEmail(result.email())
                                .orElseThrow(() -> new IllegalStateException("Registered user not found"));

                        authenticateUser(
                                user.getId(),
                                user.getPublicId().value(),
                                user.getEmail().value(),
                                user.getDisplayName(),
                                user.getRoles(),
                                request);

                        redirectAttributes.addFlashAttribute(
                                "message", "Welcome, " + result.displayName() + "! You're now logged in.");
                        redirectAttributes.addFlashAttribute("messageType", "success");

                        return "redirect:/my-table";
                    });
        } catch (IllegalArgumentException e) {
            model.addAttribute("error", e.getMessage());
            model.addAttribute("email", form.getEmail());
            model.addAttribute("displayName", form.getDisplayName());
            return "auth/register";
        }
    }

    @GetMapping("/login")
    public String showLoginForm(Model model, HttpServletRequest request) {
        request.getSession(true);
        model.addAttribute("pageTitle", "Login");
        model.addAttribute("isDemo", true);

        // If Spring Security redirected here from a protected page, treat this like logout.
        // We clear prediction + prefs localStorage (same as manual logout) to avoid stale client state.
        HttpSession session = request.getSession(false);
        Object saved = session == null ? null : session.getAttribute("SPRING_SECURITY_SAVED_REQUEST");
        if (saved instanceof SavedRequest) {
            model.addAttribute("clearTablePrediction", true);
            model.addAttribute("clearPrefs", true);
        }
        return "auth/login";
    }

    @ModelAttribute("loginForm")
    public LoginForm loginForm() {
        return new LoginForm();
    }

    @PostMapping("/login")
    public String login(
            @Valid @ModelAttribute LoginForm form,
            BindingResult bindingResult,
            RedirectAttributes redirectAttributes,
            Model model,
            HttpServletRequest request,
            HttpServletResponse response) {

        if (bindingResult.hasErrors()) {
            model.addAttribute("pageTitle", "Login");
            model.addAttribute("loginForm", form);
            return "auth/login";
        }

        try {
            Email email = Email.create(form.getEmail());
            Password.Plaintext password = Password.Plaintext.create(form.getPassword());

            User user = userRepo.findByEmail(email)
                    .filter(found -> passwordHasher.verify(password, found.getPassword()))
                    .orElse(null);

            if (user == null) {
                model.addAttribute("error", "Invalid email or password");
                model.addAttribute("pageTitle", "Login");
                model.addAttribute("loginForm", form);
                return "auth/login";
            }

            var authentication = authenticateUser(
                    user.getId(),
                    user.getPublicId().value(),
                    user.getEmail().value(),
                    user.getDisplayName(),
                    user.getRoles(),
                    request);

            rememberMeServices.loginSuccess(request, response, authentication);

            boolean switchedUserInExistingSession = isDifferentAuthenticatedSessionUser(request, user);
            UUID mainContestId = getActiveSeason().getMainContestId();
            boolean hasContestEntry =
                    mainContestId != null && contestRepo.existsByUserAndContest(user.getId(), mainContestId);

            if (switchedUserInExistingSession || hasContestEntry) {
                redirectAttributes.addFlashAttribute("clearTablePrediction", true);
                redirectAttributes.addFlashAttribute("clearPrefs", true);

                if (switchedUserInExistingSession) {
                    log.info(
                            "User {} logged in over an existing authenticated session, clearing localStorage",
                            user.getId());
                }
                if (hasContestEntry) {
                    log.info("User {} has existing contest entry, will clear auth/guest localStorage", user.getId());
                }
            }

            return "redirect:/my-table";
        } catch (IllegalArgumentException e) {
            model.addAttribute("error", e.getMessage());
            model.addAttribute("pageTitle", "Login");
            model.addAttribute("loginForm", form);
            return "auth/login";
        }
    }

    @GetMapping("/logout")
    public String logout(HttpSession session, RedirectAttributes redirectAttributes, HttpServletRequest request) {
        SecurityContextHolder.clearContext();

        if (session != null) {
            session.invalidate();
        }

        try {
            request.logout();
        } catch (Exception e) {
            log.warn("Failed to perform servlet logout", e);
        }

        // Signal frontend to clear localStorage on logout
        redirectAttributes.addFlashAttribute("clearTablePrediction", true);
        redirectAttributes.addFlashAttribute("clearPrefs", true);

        redirectAttributes.addFlashAttribute("message", "You've been logged out. See you next time!");
        redirectAttributes.addFlashAttribute("messageType", "info");

        return "redirect:/";
    }

    @GetMapping("/forgot-password")
    public String showForgotPasswordForm(Model model, HttpServletRequest request) {
        request.getSession(true);
        model.addAttribute("pageTitle", "Forgot Password");
        model.addAttribute("forgotPasswordForm", new ForgotPasswordForm());
        return "auth/forgot-password";
    }

    @PostMapping("/forgot-password")
    public String forgotPassword(
            @Valid @ModelAttribute ForgotPasswordForm form, BindingResult bindingResult, Model model) {
        if (bindingResult.hasErrors()) {
            model.addAttribute("pageTitle", "Forgot Password");
            return "auth/forgot-password";
        }

        var result = requestPasswordResetUseCase.execute(form.getEmail());
        if (result.isLeft()) {
            log.error("Error processing forgot password request");
            model.addAttribute("error", "An error occurred. Please try again later.");
            model.addAttribute("pageTitle", "Forgot Password");
            return "auth/forgot-password";
        }

        model.addAttribute("success", "If an account exists with this email, you will receive password reset instructions.");
        model.addAttribute("pageTitle", "Forgot Password");
        model.addAttribute("forgotPasswordForm", new ForgotPasswordForm());
        return "auth/forgot-password";
    }

    @GetMapping("/reset-password")
    public String showResetPasswordForm(
            @RequestParam(required = false) String token, Model model, HttpServletRequest request) {
        request.getSession(true);

        if (token == null || token.isBlank()) {
            model.addAttribute("error", "Invalid password reset link.");
            model.addAttribute("pageTitle", "Login");
            return "auth/login";
        }

        model.addAttribute("pageTitle", "Reset Password");
        ResetPasswordForm form = new ResetPasswordForm();
        form.setToken(token);
        model.addAttribute("resetPasswordForm", form);
        return "auth/reset-password";
    }

    @PostMapping("/reset-password")
    public String resetPassword(
            @Valid @ModelAttribute ResetPasswordForm form,
            BindingResult bindingResult,
            RedirectAttributes redirectAttributes,
            Model model) {
        if (bindingResult.hasErrors()) {
            model.addAttribute("pageTitle", "Reset Password");
            return "auth/reset-password";
        }

        if (!form.getNewPassword().equals(form.getConfirmPassword())) {
            bindingResult.rejectValue("confirmPassword", "password.mismatch", "Passwords do not match");
            model.addAttribute("pageTitle", "Reset Password");
            return "auth/reset-password";
        }

        var result = resetPasswordUseCase.execute(form.getToken(), form.getNewPassword());

        return result.fold(
                error -> {
                    String errorMessage = switch (error) {
                        case ResetPasswordUseCase.ResetError.InvalidToken __ ->
                                "Invalid or expired reset link. Please request a new one.";
                        case ResetPasswordUseCase.ResetError.TokenExpired __ ->
                                "This reset link has expired. Please request a new one.";
                        case ResetPasswordUseCase.ResetError.TokenAlreadyUsed __ ->
                                "This reset link has already been used. Please request a new one.";
                        case ResetPasswordUseCase.ResetError.WeakPassword weak -> weak.reason();
                    };

                    model.addAttribute("error", errorMessage);
                    model.addAttribute("pageTitle", "Reset Password");
                    return "auth/reset-password";
                },
                success -> {
                    redirectAttributes.addFlashAttribute(
                            "message", "Password reset successful! You can now log in with your new password.");
                    redirectAttributes.addFlashAttribute("messageType", "success");
                    return "redirect:/auth/login";
                });
    }

    @Data
    public static class RegisterForm {
        @NotBlank(message = "Email is required")
        @jakarta.validation.constraints.Email(message = "Invalid email format")
        private String email;

        @NotBlank(message = "Display name is required")
        @Size(min = 2, max = 100, message = "Display name must be between 2 and 100 characters")
        private String displayName;

        @NotBlank(message = "Password is required")
        @Size(min = 8, message = "Password must be at least 8 characters")
        private String password;

        @NotBlank(message = "Confirm password is required")
        private String confirmPassword;
    }

    @Data
    public static class LoginForm {
        @NotBlank(message = "Email is required")
        @jakarta.validation.constraints.Email(message = "Invalid email format")
        private String email;

        @NotBlank(message = "Password is required")
        private String password;
    }

    @Data
    public static class ForgotPasswordForm {
        @NotBlank(message = "Email is required")
        @jakarta.validation.constraints.Email(message = "Invalid email format")
        private String email;
    }

    @Data
    public static class ResetPasswordForm {
        @NotBlank(message = "Token is required")
        private String token;

        @NotBlank(message = "Password is required")
        @Size(min = 8, message = "Password must be at least 8 characters")
        private String newPassword;

        @NotBlank(message = "Confirm password is required")
        private String confirmPassword;
    }

    /**
     * Helper method to authenticate a user and create a session
     */
    private Authentication authenticateUser(
            UUID userId,
            String publicId,
            String email,
            String displayName,
            Set<Role> roles,
            HttpServletRequest request) {
        // Create authentication token with user details and roles
        List<SimpleGrantedAuthority> authorities = roles.stream()
                .map(role -> new SimpleGrantedAuthority("ROLE_" + role.name()))
                .collect(Collectors.toList());

        Authentication authentication = new UsernamePasswordAuthenticationToken(
                new WebUserDetails(userId, publicId, email, displayName, "", authorities), null, authorities);

        // Set authentication in security context
        SecurityContextHolder.getContext().setAuthentication(authentication);

        // Create session and store security context
        HttpSession session = request.getSession(true);
        session.setAttribute(
                HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY, SecurityContextHolder.getContext());

        return authentication;
    }

    private boolean isDifferentAuthenticatedSessionUser(HttpServletRequest request, User loginUser) {
        HttpSession session = request.getSession(false);
        if (session == null || loginUser == null) {
            return false;
        }

        Object contextObj = session.getAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY);
        if (!(contextObj instanceof SecurityContext securityContext)) {
            return false;
        }

        Authentication existingAuth = securityContext.getAuthentication();
        if (existingAuth == null || !existingAuth.isAuthenticated()) {
            return false;
        }

        Object principal = existingAuth.getPrincipal();
        if (principal instanceof WebUserDetails webUserDetails) {
            return !loginUser.getId().equals(webUserDetails.getUserId());
        }

        String loginEmail = loginUser.getEmail().value();
        if (principal instanceof UserDetails userDetails) {
            return !loginEmail.equalsIgnoreCase(userDetails.getUsername());
        }

        if (principal instanceof String name) {
            if ("anonymousUser".equalsIgnoreCase(name)) {
                return false;
            }
            return !loginEmail.equalsIgnoreCase(name);
        }

        return false;
    }

    private Season getActiveSeason() {
        return seasonRepo
                .findMostRecentSeason(competitionDefaults.defaultCompetitionSlug())
                .orElseThrow(() -> new IllegalStateException("No active season available"));
    }
}
