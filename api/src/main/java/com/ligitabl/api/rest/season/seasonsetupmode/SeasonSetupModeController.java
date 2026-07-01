package com.ligitabl.api.rest.season.seasonsetupmode;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.ligitabl.api.shared.exceptions.UseCaseException;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@RestController
@RequestMapping("/api/seasons")
@RequiredArgsConstructor
@Slf4j
public class SeasonSetupModeController {

    private final ManageSeasonSetupModeUseCase useCase;

    /**
     * POST /api/seasons/{seasonSlug}/setup-mode/enter
     * POST /api/seasons/{seasonSlug}/setup-mode/enter?competition=bundesliga
     */
    @PostMapping("/{seasonSlug}/setup-mode/enter")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<SetupModeResult> enter(
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
                        ResponseEntity::ok);
    }

    /**
     * POST /api/seasons/{seasonSlug}/setup-mode/leave
     * POST /api/seasons/{seasonSlug}/setup-mode/leave?competition=bundesliga
     */
    @PostMapping("/{seasonSlug}/setup-mode/leave")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<SetupModeResult> leave(
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
                        ResponseEntity::ok);
    }
}
