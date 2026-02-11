package com.ligitabl.api.web.predictions.latestresult;

import java.security.Principal;
import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import com.ligitabl.model.auth.Email;
import com.ligitabl.model.domain.User;
import com.ligitabl.model.repo.UserRepo;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Controller
@RequestMapping("/predictions/user")
@RequiredArgsConstructor
@Slf4j
public class LatestResultBannerController {
    private final GetLatestResultUseCase getLatestResultUseCase;
    private final DismissResultBannerUseCase dismissResultBannerUseCase;
    private final UserRepo userRepo;

    @GetMapping("/me/latest-result-banner")
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
                optionalResult -> {
                    if (optionalResult.isEmpty()) {
                        return "fragments/results-banner :: results-banner-user(result=null)";
                    }
                    model.addAttribute("result", optionalResult.get());
                    return "fragments/results-banner :: results-banner-user(result=${result})";
                });
    }

    @PostMapping("/me/latest-result-banner/dismiss")
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

    private UUID resolveUserId(Principal principal) {
        if (principal == null
                || principal.getName() == null
                || principal.getName().isBlank()) {
            return null;
        }
        try {
            Email email = Email.create(principal.getName());
            return userRepo.findByEmail(email).map(User::getId).orElse(null);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
