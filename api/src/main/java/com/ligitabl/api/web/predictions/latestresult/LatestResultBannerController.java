package com.ligitabl.api.web.predictions.latestresult;

import java.security.Principal;
import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import com.ligitabl.api.auth.security.WebUserDetails;
import com.ligitabl.api.web.shared.security.WebSecurity;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Controller
@RequestMapping("/my-table")
@RequiredArgsConstructor
@Slf4j
public class LatestResultBannerController {
    private final GetLatestResultUseCase getLatestResultUseCase;
    private final DismissResultBannerUseCase dismissResultBannerUseCase;

    @GetMapping("/latest-result-banner")
    public String getLatestResultBanner(Principal principal, Model model) {
        UUID userId = resolveUserId(principal);
        if (userId == null) {
            return "fragments/results-banner :: results-banner-user(result=null)";
        }

        var result = getLatestResultUseCase.execute(userId);

        return result.fold(
                error -> {
                    log.debug("Error fetching latest result banner: {}", error.getMessage());
                    return "fragments/results-banner :: results-banner-user(result=null)";
                },
                bannerResult -> {
                    if (bannerResult.isEmpty()) {
                        return "fragments/results-banner :: results-banner-user(result=null)";
                    }
                    model.addAttribute("result", bannerResult.get());
                    return "fragments/results-banner :: results-banner-user(result=${result})";
                });
    }

    @PostMapping("/latest-result-banner/dismiss")
    @ResponseBody
    public ResponseEntity<Void> dismissBanner(@RequestParam int round, Principal principal) {
        UUID userId = resolveUserId(principal);
        if (userId == null) {
            return ResponseEntity.status(401).build();
        }

        var result = dismissResultBannerUseCase.execute(userId, round);

        return result.fold(
                error -> {
                    log.debug("Error dismissing result banner: {}", error.getMessage());
                    return ResponseEntity.notFound().<Void>build();
                },
                success -> ResponseEntity.noContent().<Void>build());
    }

    /** Follows the effective user, so an impersonating admin sees the impersonated user's result. */
    private UUID resolveUserId(Principal principal) {
        WebUserDetails user = WebSecurity.resolveUser(principal);
        return user == null ? null : user.getUserId();
    }
}
