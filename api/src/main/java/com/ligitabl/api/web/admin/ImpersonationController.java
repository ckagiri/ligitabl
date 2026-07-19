package com.ligitabl.api.web.admin;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import com.ligitabl.api.auth.impersonation.ImpersonationAuthorizationService;
import com.ligitabl.api.auth.impersonation.PlayerImpersonationService;
import com.ligitabl.api.auth.security.WebUserDetails;

import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Controller
@RequiredArgsConstructor
@Slf4j
public class ImpersonationController {

    private final PlayerImpersonationService playerImpersonationService;

    @GetMapping("/admin/impersonation/modal")
    @PreAuthorize("hasRole('ADMIN')")
    public String modal() {
        return "fragments/impersonation-modal :: modal";
    }

    @PostMapping("/admin/impersonation/start")
    @PreAuthorize("hasRole('ADMIN')")
    public Object start(
            @AuthenticationPrincipal WebUserDetails userDetails,
            @RequestParam String identifier,
            HttpSession session,
            Model model) {

        var result = playerImpersonationService.start(userDetails.getUserId(), identifier, session);

        if (result instanceof ImpersonationAuthorizationService.Result.Ok) {
            // Land on the impersonated user's table — the page support sessions start from
            return ResponseEntity.noContent().header("HX-Redirect", "/my-table").build();
        }

        // Re-render the modal with an inline error; htmx swaps it back into the modal root
        model.addAttribute("identifier", identifier);
        model.addAttribute(
                "error",
                switch (result) {
                    case ImpersonationAuthorizationService.Result.TargetNotFound e -> "No user found for '"
                            + e.identifier() + "'.";
                    case ImpersonationAuthorizationService.Result.SelfImpersonation
                    e -> "You cannot impersonate yourself.";
                    case ImpersonationAuthorizationService.Result.TargetPrivileged
                    e -> "Privileged accounts cannot be impersonated.";
                    default -> "Impersonation is not allowed.";
                });
        return "fragments/impersonation-modal :: modal";
    }

    @PostMapping("/admin/impersonation/stop")
    public ResponseEntity<Void> stop(HttpSession session) {
        // Deliberately not ADMIN-gated: any impersonating session may stop itself
        playerImpersonationService.stop(session);
        return ResponseEntity.noContent().header("HX-Redirect", "/").build();
    }

    @GetMapping("/fragments/impersonation-banner")
    public String banner() {
        // Renders empty unless NavbarControllerAdvice reports an active impersonation
        return "fragments/impersonation-banner :: banner";
    }
}
