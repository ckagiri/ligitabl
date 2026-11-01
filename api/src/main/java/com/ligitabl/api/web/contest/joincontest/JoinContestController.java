package com.ligitabl.api.web.contest.joincontest;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.Principal;
import java.util.UUID;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import com.ligitabl.api.auth.security.WebUserDetails;
import com.ligitabl.api.usecase.contest.joinprivatecontest.JoinPrivateContestCommand;
import com.ligitabl.api.usecase.contest.joinprivatecontest.JoinPrivateContestError;
import com.ligitabl.api.usecase.contest.joinprivatecontest.JoinPrivateContestUseCase;
import com.ligitabl.api.usecase.contest.previewcontestbycode.ContestPreviewDto;
import com.ligitabl.api.usecase.contest.previewcontestbycode.PreviewContestByCodeError;
import com.ligitabl.api.usecase.contest.previewcontestbycode.PreviewContestByCodeUseCase;
import com.ligitabl.api.web.shared.security.WebSecurity;
import com.ligitabl.model.repo.ContestRepo;
import com.ligitabl.model.repo.SeasonPredictionRepo;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Controller
@RequestMapping("/contests")
@RequiredArgsConstructor
@Slf4j
public class JoinContestController {

    private final PreviewContestByCodeUseCase previewContestByCodeUseCase;
    private final JoinPrivateContestUseCase joinPrivateContestUseCase;
    private final ContestRepo contestRepo;
    private final SeasonPredictionRepo predictionRepo;

    /**
     * No code → blank form; invalid code → error card; no prediction → redirect to
     * /predictions?next=...; valid → preview card and confirm button.
     */
    @GetMapping("/join")
    public String joinForm(
            @RequestParam(name = "code", required = false) String rawCode, Model model, Principal principal) {

        WebUserDetails user = WebSecurity.resolveUser(principal);
        if (user == null) return "redirect:/auth/login";

        String code = normalize(rawCode);
        if (code == null) {
            return "contest/join";
        }

        var previewResult = previewContestByCodeUseCase.execute(code);
        if (previewResult.isLeft()) {
            model.addAttribute("errorCode", toPreviewErrorCode(previewResult.getLeft()));
            model.addAttribute("code", code);
            return "contest/join";
        }

        ContestPreviewDto preview = previewResult.get();

        // Prediction prerequisite: look up full contest to get seasonId
        UUID userId = user.getUserId();
        var contest = contestRepo.findByJoinCode(code).orElse(null);
        if (contest != null && !predictionRepo.existsByUserAndSeason(userId, contest.getSeasonId())) {
            String nextUrl = "/contests/join?code=" + URLEncoder.encode(code, StandardCharsets.UTF_8);
            return "redirect:/predictions/user/me?next=" + URLEncoder.encode(nextUrl, StandardCharsets.UTF_8);
        }

        model.addAttribute("contest", preview);
        model.addAttribute("code", code);
        return "contest/join";
    }

    /** GET /contests/join/preview — HTMX fragment; fires after 7-char input on /contests page. */
    @GetMapping("/join/preview")
    public String joinPreview(@RequestParam(name = "code") String rawCode, Model model, Principal principal) {

        WebUserDetails user = WebSecurity.resolveUser(principal);
        if (user == null) {
            model.addAttribute("errorCode", "NOT_FOUND");
            return "contest-join :: preview-fragment";
        }

        String code = normalize(rawCode);
        if (code == null) {
            model.addAttribute("errorCode", "NOT_FOUND");
            return "contest/join :: preview-fragment";
        }

        var previewResult = previewContestByCodeUseCase.execute(code);
        if (previewResult.isLeft()) {
            model.addAttribute("errorCode", toPreviewErrorCode(previewResult.getLeft()));
            return "contest/join :: preview-fragment";
        }

        model.addAttribute("contest", previewResult.get());
        model.addAttribute("code", code);
        return "contest/join :: preview-fragment";
    }

    /** POST /contests/join/confirm — executes the join, redirects to /contests/{id} on success. */
    @PostMapping("/join/confirm")
    public String joinConfirm(@RequestParam(name = "code") String rawCode, Model model, Principal principal) {

        WebUserDetails user = WebSecurity.resolveUser(principal);
        if (user == null) return "redirect:/auth/login";

        String code = normalize(rawCode);
        if (code == null) {
            model.addAttribute("errorCode", "NOT_FOUND");
            return "contest/join";
        }

        var result = joinPrivateContestUseCase.execute(new JoinPrivateContestCommand(user.getUserId(), code));
        return result.fold(
                error -> {
                    model.addAttribute("errorCode", toJoinErrorCode(error));
                    model.addAttribute("code", code);
                    return "contest/join";
                },
                success -> "redirect:/contests/" + success.contestId());
    }

    /**
     * Folds the code to lowercase before lookup or echo, whatever caps a keyboard or old invite
     * added; matching is case-insensitive anyway. Null for a blank code.
     */
    private static String normalize(String code) {
        if (code == null || code.isBlank()) {
            return null;
        }
        return code.trim().toLowerCase();
    }

    private static String toPreviewErrorCode(PreviewContestByCodeError error) {
        return switch (error) {
            case PreviewContestByCodeError.ContestClosed ignored -> "CLOSED";
            default -> "NOT_FOUND";
        };
    }

    private static String toJoinErrorCode(JoinPrivateContestError error) {
        return switch (error) {
            case JoinPrivateContestError.ContestNotFound ignored -> "NOT_FOUND";
            case JoinPrivateContestError.ContestClosed ignored -> "CLOSED";
            case JoinPrivateContestError.SeasonNotJoinable ignored -> "CLOSED";
            case JoinPrivateContestError.AlreadyMember ignored -> "ALREADY_MEMBER";
            case JoinPrivateContestError.MemberCapReached ignored -> "CAP_REACHED";
            case JoinPrivateContestError.JoinWindowClosed ignored -> "WINDOW_CLOSED";
            case JoinPrivateContestError.MaxContestsReached ignored -> "MAX_CONTESTS";
            case JoinPrivateContestError.NoPrediction ignored -> "NO_PREDICTION";
            default -> "ERROR";
        };
    }
}
