package com.ligitabl.api.web.auth;

import com.ligitabl.api.auth.security.WebUserDetails;
import com.ligitabl.api.config.CompetitionDefaults;
import com.ligitabl.api.shared.Either;
import com.ligitabl.api.shared.errors.UseCaseError;
import com.ligitabl.api.rest.auth.register.RegisterCommand;
import com.ligitabl.api.rest.auth.register.RegisterResult;
import com.ligitabl.api.rest.auth.register.RegisterUseCase;
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
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

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

    @GetMapping("/register")
    public String showRegisterForm(Model model) {
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

                        redirectAttributes.addFlashAttribute("message",
                                "Welcome, " + result.displayName() + "! You're now logged in.");
                        redirectAttributes.addFlashAttribute("messageType", "success");

                        return "redirect:/predictions/user/me";
                    });
        } catch (IllegalArgumentException e) {
            model.addAttribute("error", e.getMessage());
            model.addAttribute("email", form.getEmail());
            model.addAttribute("displayName", form.getDisplayName());
            return "auth/register";
        }
    }

    @GetMapping("/login")
    public String showLoginForm(Model model) {
        model.addAttribute("pageTitle", "Login");
        model.addAttribute("isDemo", true);
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
            HttpServletRequest request) {

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

            authenticateUser(
                    user.getId(),
                    user.getPublicId().value(),
                    user.getEmail().value(),
                    user.getDisplayName(),
                    user.getRoles(),
                    request);

            UUID mainContestId = getActiveSeason().getMainContestId();
            if (mainContestId != null && contestRepo.existsByUserAndContest(user.getId(), mainContestId)) {
                redirectAttributes.addFlashAttribute("clearGuestPrediction", true);
                log.info("User {} has existing contest entry, will clear guest localStorage", user.getId());
            }

            redirectAttributes.addFlashAttribute("message",
                    "Welcome back, " + user.getDisplayName() + "!");
            redirectAttributes.addFlashAttribute("messageType", "success");

            return "redirect:/predictions/user/me";
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

        // Signal frontend to clear guest localStorage on logout
        redirectAttributes.addFlashAttribute("clearGuestPrediction", true);

        redirectAttributes.addFlashAttribute("message",
                "You've been logged out. See you next time!");
        redirectAttributes.addFlashAttribute("messageType", "info");

        return "redirect:/";
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


    /**
     * Helper method to authenticate a user and create a session
     */
    private void authenticateUser(
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
    }

    private Season getActiveSeason() {
        return seasonRepo.findMostRecentSeason(competitionDefaults.defaultCompetitionSlug())
                .orElseThrow(() -> new IllegalStateException("No active season available"));
    }

}
