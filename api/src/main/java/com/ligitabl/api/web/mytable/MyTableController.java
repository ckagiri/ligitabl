package com.ligitabl.api.web.mytable;

import java.security.Principal;

import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * Canonical "My Table" routes.
 *
 * <p>These routes redirect/forward to the existing predictions pages:
 * <ul>
 *   <li>/my-table -> /predictions/user/me (authenticated) or /my-table/guest (guest)</li>
 *   <li>/my-table/guest -> /my-table (authenticated) or /predictions/user/guest (guest)</li>
 *   <li>/my-table/what-if -> /predictions/user/what-if (authenticated) or /my-table (guest)</li>
 * </ul>
 */
@Controller
public class MyTableController {

    @GetMapping("/my-table")
    public String myTable(@RequestParam(required = false) Integer round, Principal principal) {
        if (!isAuthenticatedUser()) {
            return withRoundRedirect("/my-table/guest", round);
        }
        return withRoundForward("/predictions/user/me", round);
    }

    @GetMapping("/my-table/guest")
    public String guestTable(@RequestParam(required = false) Integer round, Principal principal) {
        if (isAuthenticatedUser()) {
            return withRoundRedirect("/my-table", round);
        }
        return withRoundForward("/predictions/user/guest", round);
    }

    /**
     * <p>Guests are sent to {@code /my-table} rather than {@code /predictions/user/what-if}, which
     * bounces them anyway: better to land somewhere useful than to be redirected twice.
     */
    @GetMapping("/my-table/what-if")
    public String whatIf() {
        if (!isAuthenticatedUser()) {
            return "redirect:/my-table";
        }
        return "forward:/predictions/user/what-if";
    }

    private boolean isAuthenticatedUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            return false;
        }
        if (authentication instanceof AnonymousAuthenticationToken) {
            return false;
        }
        Object principal = authentication.getPrincipal();
        return !(principal instanceof String value && "anonymousUser".equalsIgnoreCase(value));
    }

    private String withRoundForward(String basePath, Integer round) {
        if (round == null) {
            return "forward:" + basePath;
        }
        return "forward:" + basePath + "?round=" + round;
    }

    private String withRoundRedirect(String basePath, Integer round) {
        if (round == null) {
            return "redirect:" + basePath;
        }
        return "redirect:" + basePath + "?round=" + round;
    }
}
