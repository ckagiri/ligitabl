package com.ligitabl.api.web.mytable;

import java.security.Principal;

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
 * </ul>
 */
@Controller
public class MyTableController {

    @GetMapping("/my-table")
    public String myTable(@RequestParam(required = false) Integer round, Principal principal) {
        if (principal == null) {
            return withRoundRedirect("/my-table/guest", round);
        }
        return withRoundForward("/predictions/user/me", round);
    }

    @GetMapping("/my-table/guest")
    public String guestTable(@RequestParam(required = false) Integer round, Principal principal) {
        if (principal != null) {
            return withRoundRedirect("/my-table", round);
        }
        return withRoundForward("/predictions/user/guest", round);
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
