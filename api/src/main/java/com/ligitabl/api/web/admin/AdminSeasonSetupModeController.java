package com.ligitabl.api.web.admin;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import com.ligitabl.api.rest.season.seasonsetupmode.ManageSeasonSetupModeUseCase;
import com.ligitabl.api.rest.season.seasonsetupmode.SeasonSetupModeCommand;
import com.ligitabl.api.rest.season.seasonsetupmode.SetupModeAction;
import com.ligitabl.api.shared.exceptions.UseCaseException;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Controller("webAdminSeasonSetupModeController")
@RequestMapping("/admin/seasons")
@RequiredArgsConstructor
@Slf4j
public class AdminSeasonSetupModeController {

    private final ManageSeasonSetupModeUseCase useCase;

    /**
     * POST /admin/seasons/{seasonSlug}/setup-mode/enter
     * POST /admin/seasons/{seasonSlug}/setup-mode/enter?competition=bundesliga
     */
    @PostMapping("/{seasonSlug}/setup-mode/enter")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    @ResponseBody
    public ResponseEntity<?> enter(
            @PathVariable String seasonSlug, @RequestParam(required = false) String competition) {

        log.info("Enter setup mode: season={}", seasonSlug);

        var cmd = SeasonSetupModeCommand.builder()
                .competitionIdentifier(competition)
                .seasonSlug(seasonSlug)
                .action(SetupModeAction.ENTER)
                .build();

        return useCase.execute(cmd)
                .fold(
                        err -> {
                            throw new UseCaseException(err);
                        },
                        result -> ResponseEntity.ok()
                                .header("HX-Redirect", "/rounds/current/matches")
                                .build());
    }

    /**
     * POST /admin/seasons/{seasonSlug}/setup-mode/leave
     * POST /admin/seasons/{seasonSlug}/setup-mode/leave?competition=bundesliga
     */
    @PostMapping("/{seasonSlug}/setup-mode/leave")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    @ResponseBody
    public ResponseEntity<?> leave(
            @PathVariable String seasonSlug, @RequestParam(required = false) String competition) {

        log.info("Leave setup mode: season={}", seasonSlug);

        var cmd = SeasonSetupModeCommand.builder()
                .competitionIdentifier(competition)
                .seasonSlug(seasonSlug)
                .action(SetupModeAction.LEAVE)
                .build();

        return useCase.execute(cmd)
                .fold(
                        err -> {
                            throw new UseCaseException(err);
                        },
                        result -> ResponseEntity.ok()
                                .header("HX-Redirect", "/rounds/current/matches")
                                .build());
    }
}
