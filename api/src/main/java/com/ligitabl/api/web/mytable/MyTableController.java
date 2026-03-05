package com.ligitabl.api.web.mytable;

import java.security.Principal;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * Canonical "My Table" routes.
 *
 * <p>These routes forward to the existing predictions pages:
 * <ul>
 *   <li>/my-table -> /predictions/user/me (authenticated) or /my-table/guest (guest)</li>
 *   <li>/my-table/guest -> /predictions/user/guest</li>
 * </ul>
 */
@Controller
public class MyTableController {

    @GetMapping("/my-table")
    public String myTable(@RequestParam(required = false) Integer round, Principal principal) {
        String forwardTo = principal == null ? "/my-table/guest" : "/predictions/user/me";
        return withRoundForward(forwardTo, round);
    }

    @GetMapping("/my-table/guest")
    public String guestTable(@RequestParam(required = false) Integer round) {
        return withRoundForward("/predictions/user/guest", round);
    }

    private String withRoundForward(String basePath, Integer round) {
        if (round == null) {
            return "forward:" + basePath;
        }
        return "forward:" + basePath + "?round=" + round;
    }
}
