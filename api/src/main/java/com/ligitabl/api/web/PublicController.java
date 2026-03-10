package com.ligitabl.api.web;

import java.security.Principal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * Public controller for home page.
 */
@Controller
public class PublicController {
    @GetMapping("/")
    public String home(Model model, Principal principal) {
        if (principal != null) {
            return "redirect:/my-table";
        }
        model.addAttribute("pageTitle", "Home");
        return "index";
    }

    @GetMapping("/home")
    public String homeNoRedirect(Model model) {
        model.addAttribute("pageTitle", "Home");
        return "index";
    }

    @GetMapping("/favicon.ico")
    public String favicon() {
        return "redirect:/favicon.svg";
    }
}
