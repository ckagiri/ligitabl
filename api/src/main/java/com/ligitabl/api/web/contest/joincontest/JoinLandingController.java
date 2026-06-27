package com.ligitabl.api.web.contest.joincontest;

import java.security.Principal;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import com.ligitabl.api.web.shared.security.WebSecurity;

import lombok.RequiredArgsConstructor;

@Controller
@RequiredArgsConstructor
public class JoinLandingController {

    /**
     * Public landing page for contest invite links.
     *
     * - Authenticated users are sent straight to /contests/join (existing flow).
     * - Guests see a code-entry form. Submitting the form redirects to /i/{code}
     *   which triggers authentication and then drops the user onto the join-preview page.
     */
    @GetMapping("/join")
    public String joinLanding(
            @RequestParam(required = false) String code,
            Principal principal) {

        if (WebSecurity.resolveUser(principal) != null) {
            if (code != null && !code.isBlank()) {
                return "redirect:/contests/join?code=" + code;
            }
            return "redirect:/contests/join";
        }

        if (code != null && !code.isBlank()) {
            return "redirect:/i/" + code.toUpperCase();
        }

        return "contest/join-landing";
    }
}
