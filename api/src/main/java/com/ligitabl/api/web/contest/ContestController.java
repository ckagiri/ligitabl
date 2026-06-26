package com.ligitabl.api.web.contest;

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

import com.ligitabl.api.rest.contest.joinprivatecontest.JoinPrivateContestCommand;
import com.ligitabl.api.rest.contest.joinprivatecontest.JoinPrivateContestError;
import com.ligitabl.api.rest.contest.joinprivatecontest.JoinPrivateContestUseCase;
import com.ligitabl.api.rest.contest.previewcontestbycode.ContestPreviewDto;
import com.ligitabl.api.rest.contest.previewcontestbycode.PreviewContestByCodeError;
import com.ligitabl.api.rest.contest.previewcontestbycode.PreviewContestByCodeUseCase;
import com.ligitabl.model.auth.Email;
import com.ligitabl.model.domain.User;
import com.ligitabl.model.repo.ContestRepo;
import com.ligitabl.model.repo.SeasonPredictionRepo;
import com.ligitabl.model.repo.UserRepo;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Controller
@RequestMapping("/contests")
@RequiredArgsConstructor
@Slf4j
public class ContestController {

    private final PreviewContestByCodeUseCase previewContestByCodeUseCase;
    private final JoinPrivateContestUseCase joinPrivateContestUseCase;
    private final ContestRepo contestRepo;
    private final SeasonPredictionRepo predictionRepo;
    private final UserRepo userRepo;

    /**
     * GET /contests/join
     *
     * Three states:
     *  - no code → blank form
     *  - code + invalid/closed → error card
     *  - code + valid + no prediction → redirect to /predictions?next=...
     *  - code + valid + has prediction → preview card + confirm button
     */
    @GetMapping("/join")
    public String joinForm(
            @RequestParam(required = false) String code,
            Model model,
            Principal principal) {

        UUID userId = resolveUserId(principal);
        if (userId == null) {
            return "redirect:/auth/login";
        }

        if (code == null || code.isBlank()) {
            return "contest-join";
        }

        var previewResult = previewContestByCodeUseCase.execute(code);
        if (previewResult.isLeft()) {
            model.addAttribute("errorCode", previewErrorCode(previewResult.getLeft()));
            model.addAttribute("code", code);
            return "contest-join";
        }

        ContestPreviewDto preview = previewResult.get();

        // Check prediction prerequisite using the contest's seasonId
        var contest = contestRepo.findByJoinCode(code).orElse(null);
        if (contest != null && !predictionRepo.existsByUserAndSeason(userId, contest.getSeasonId())) {
            String nextUrl = "/contests/join?code=" + URLEncoder.encode(code, StandardCharsets.UTF_8);
            return "redirect:/predictions?next=" + URLEncoder.encode(nextUrl, StandardCharsets.UTF_8);
        }

        model.addAttribute("contest", preview);
        model.addAttribute("code", code);
        return "contest-join";
    }

    /**
     * GET /contests/join/preview — HTMX fragment, fires after 7-char input on /contests page.
     */
    @GetMapping("/join/preview")
    public String joinPreview(
            @RequestParam String code,
            Model model,
            Principal principal) {

        UUID userId = resolveUserId(principal);
        if (userId == null) {
            model.addAttribute("errorCode", "NOT_FOUND");
            return "contest-join :: preview-fragment";
        }

        var previewResult = previewContestByCodeUseCase.execute(code);
        if (previewResult.isLeft()) {
            model.addAttribute("errorCode", previewErrorCode(previewResult.getLeft()));
            return "contest-join :: preview-fragment";
        }

        model.addAttribute("contest", previewResult.get());
        model.addAttribute("code", code);
        return "contest-join :: preview-fragment";
    }

    /**
     * POST /contests/join/confirm — executes the join, redirects to /contests/{id} on success.
     */
    @PostMapping("/join/confirm")
    public String joinConfirm(
            @RequestParam String code,
            Model model,
            Principal principal) {

        UUID userId = resolveUserId(principal);
        if (userId == null) {
            return "redirect:/auth/login";
        }

        var result = joinPrivateContestUseCase.execute(new JoinPrivateContestCommand(userId, code));
        return result.fold(
                error -> {
                    model.addAttribute("errorCode", joinErrorCode(error));
                    model.addAttribute("code", code);
                    return "contest-join";
                },
                success -> "redirect:/contests/" + success.contestId());
    }

    private UUID resolveUserId(Principal principal) {
        if (principal == null || principal.getName() == null || principal.getName().isBlank()) {
            return null;
        }
        try {
            Email email = Email.create(principal.getName());
            return userRepo.findByEmail(email).map(User::getId).orElse(null);
        } catch (IllegalArgumentException e) {
            log.warn("Could not resolve user from principal: {}", e.getMessage());
            return null;
        }
    }

    private static String previewErrorCode(PreviewContestByCodeError error) {
        return switch (error) {
            case PreviewContestByCodeError.ContestClosed ignored -> "CLOSED";
            default -> "NOT_FOUND";
        };
    }

    private static String joinErrorCode(JoinPrivateContestError error) {
        return switch (error) {
            case JoinPrivateContestError.ContestNotFound ignored -> "NOT_FOUND";
            case JoinPrivateContestError.ContestClosed ignored -> "CLOSED";
            case JoinPrivateContestError.AlreadyMember ignored -> "ALREADY_MEMBER";
            case JoinPrivateContestError.MemberCapReached ignored -> "CAP_REACHED";
            case JoinPrivateContestError.JoinWindowClosed ignored -> "WINDOW_CLOSED";
            case JoinPrivateContestError.MaxContestsReached ignored -> "MAX_CONTESTS";
            case JoinPrivateContestError.NoPrediction ignored -> "NO_PREDICTION";
            default -> "ERROR";
        };
    }
}
