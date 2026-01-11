package com.ligitabl.api.controllers.web;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.ligitabl.api.shared.Either;
import com.ligitabl.api.shared.errors.UseCaseError;
import com.ligitabl.api.usecases.auth.register.RegisterCommand;
import com.ligitabl.api.usecases.auth.register.RegisterResult;
import com.ligitabl.api.usecases.auth.register.RegisterUseCase;
import com.ligitabl.model.auth.Email;
import com.ligitabl.model.auth.Password;
import com.ligitabl.model.auth.Role;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;

import java.util.List;
import java.util.stream.Collectors;

@Controller
@org.springframework.web.bind.annotation.RequestMapping("/auth")
@RequiredArgsConstructor
public class WebAuthController {

    private final RegisterUseCase registerUseCase;

    @GetMapping("/login")
    public String loginPage(Model model) {
        model.addAttribute("pageTitle", "Login");
        return "auth/login";
    }

    @GetMapping("/register")
    public String registerPage(Model model) {
        model.addAttribute("pageTitle", "Register");
        return "auth/register";
    }

    @PostMapping("/register")
    public String register(
            @ModelAttribute RegisterRequest request,
            HttpServletRequest httpRequest,
            RedirectAttributes redirectAttributes,
            Model model) {

        try {
            Email email = Email.create(request.email());
            Password.Plaintext password = Password.Plaintext.create(request.password());

            var registerCommand = new RegisterCommand(email, request.displayName(), password);

            Either<UseCaseError, RegisterResult> result = registerUseCase.execute(registerCommand);

            return result.fold(
                    error -> {
                        // Registration failed - show error on register page
                        model.addAttribute("error", error.getMessage());
                        model.addAttribute("email", request.email());
                        model.addAttribute("displayName", request.displayName());
                        return "auth/register";
                    },
                    registerResult -> {
                        // Registration successful - log the user in automatically
                        authenticateUser(registerResult.email().value(), registerResult.roles(), httpRequest);

                        // Redirect to predictions page
                        return "redirect:/predictions/me";
                    });
        } catch (IllegalArgumentException e) {
            // Validation error from domain types (Email or Password)
            model.addAttribute("error", e.getMessage());
            model.addAttribute("email", request.email());
            model.addAttribute("displayName", request.displayName());
            return "auth/register";
        }
    }

    /**
     * Helper method to authenticate a user and create a session
     */
    private void authenticateUser(String email, java.util.Set<Role> roles, HttpServletRequest request) {
        // Create authentication token with user details and roles
        List<SimpleGrantedAuthority> authorities = roles.stream()
                .map(role -> new SimpleGrantedAuthority("ROLE_" + role.name()))
                .collect(Collectors.toList());

        Authentication authentication = new UsernamePasswordAuthenticationToken(email, null, authorities);

        // Set authentication in security context
        SecurityContextHolder.getContext().setAuthentication(authentication);

        // Create session and store security context
        HttpSession session = request.getSession(true);
        session.setAttribute(
                HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY,
                SecurityContextHolder.getContext());
    }

    /**
     * Form binding record for registration
     */
    public record RegisterRequest(String email, String displayName, String password) {}
}
