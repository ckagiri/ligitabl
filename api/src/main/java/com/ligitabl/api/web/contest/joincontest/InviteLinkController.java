package com.ligitabl.api.web.contest.joincontest;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@Controller
public class InviteLinkController {

    @GetMapping("/i/{code}")
    public String inviteLink(@PathVariable String code) {
        return "redirect:/contests/join?code=" + code;
    }
}
