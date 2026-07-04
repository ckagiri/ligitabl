package com.ligitabl.api.web.rounds;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;

import com.ligitabl.api.config.CompetitionDefaults;
import com.ligitabl.api.rest.round.advanceround.AdvanceCurrentRoundNowUseCase;
import com.ligitabl.api.rest.round.advanceround.CancelRoundAdvancementUseCase;
import com.ligitabl.api.rest.round.finalizeround.FinalizeRoundCommand;
import com.ligitabl.api.rest.round.finalizeround.FinalizeRoundError;
import com.ligitabl.api.rest.round.finalizeround.FinalizeRoundUseCase;
import com.ligitabl.api.shared.errors.ConflictError;
import com.ligitabl.api.shared.errors.NotFoundError;
import com.ligitabl.api.shared.errors.UseCaseError;
import com.ligitabl.api.shared.errors.ValidationError;
import com.ligitabl.model.repo.SeasonRepo;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Controller("webFinalizeAndAdvanceRoundController")
@RequestMapping("/rounds")
@RequiredArgsConstructor
@Slf4j
public class FinalizeAndAdvanceRoundController {

    private final SeasonRepo seasonRepo;
    private final CompetitionDefaults competitionDefaults;
    private final FinalizeRoundUseCase finalizeRoundUseCase;
    private final AdvanceCurrentRoundNowUseCase advanceCurrentRoundNowUseCase;
    private final CancelRoundAdvancementUseCase cancelRoundAdvancementUseCase;

    @PostMapping("/current/finalize-and-advance")
    @PreAuthorize("hasRole('ADMIN')")
    @ResponseBody
    public ResponseEntity<?> finalizeAndAdvance() {
        log.info("POST /rounds/current/finalize-and-advance");

        var season = seasonRepo
                .findActiveSeason(competitionDefaults.defaultCompetitionSlug())
                .orElseThrow(() -> new IllegalStateException("No active season"));

        var finalizeResult = finalizeRoundUseCase.execute(new FinalizeRoundCommand(season.getId(), null, false));

        if (finalizeResult.isLeft()) {
            var error = finalizeResult.getLeft();
            log.error("Finalize failed: {}", error);
            return toFinalizeErrorResponse(error);
        }

        var advanceResult = advanceCurrentRoundNowUseCase.execute();
        if (advanceResult.isLeft()) {
            log.warn("Finalization succeeded but advancement failed: {}", advanceResult.getLeft());
        }

        return ResponseEntity.ok()
                .header("HX-Redirect", "/rounds/current/matches")
                .build();
    }

    @PostMapping("/current/finalize")
    @PreAuthorize("hasRole('ADMIN')")
    @ResponseBody
    public ResponseEntity<?> finalizeRound() {
        log.info("POST /rounds/current/finalize");

        var season = seasonRepo
                .findActiveSeason(competitionDefaults.defaultCompetitionSlug())
                .orElseThrow(() -> new IllegalStateException("No active season"));

        var finalizeResult = finalizeRoundUseCase.execute(new FinalizeRoundCommand(season.getId(), null, false));

        if (finalizeResult.isLeft()) {
            var error = finalizeResult.getLeft();
            log.error("Finalize failed: {}", error);
            return toFinalizeErrorResponse(error);
        }

        return ResponseEntity.ok()
                .header("HX-Redirect", "/rounds/current/matches")
                .build();
    }

    @PostMapping("/current/advance")
    @PreAuthorize("hasRole('ADMIN')")
    @ResponseBody
    public ResponseEntity<?> advance() {
        log.info("POST /rounds/current/advance");

        return advanceCurrentRoundNowUseCase.execute().fold(this::toUseCaseErrorResponse, result -> ResponseEntity.ok()
                .header("HX-Redirect", "/rounds/current/matches")
                .build());
    }

    @PostMapping("/current/cancel-advancement")
    @PreAuthorize("hasRole('ADMIN')")
    @ResponseBody
    public ResponseEntity<?> cancelAdvancement() {
        log.info("POST /rounds/current/cancel-advancement");

        return cancelRoundAdvancementUseCase.execute().fold(this::toUseCaseErrorResponse, result -> ResponseEntity.ok()
                .header("HX-Redirect", "/rounds/current/matches")
                .build());
    }

    private ResponseEntity<?> toFinalizeErrorResponse(FinalizeRoundError error) {
        return switch (error) {
            case FinalizeRoundError.RoundObstructed e -> ResponseEntity.status(409)
                    .body(e.message());
            case FinalizeRoundError.RoundNotReady e -> ResponseEntity.badRequest()
                    .body(e.reason());
            default -> ResponseEntity.internalServerError().body(String.valueOf(error));
        };
    }

    private ResponseEntity<?> toUseCaseErrorResponse(UseCaseError error) {
        log.warn("Round advancement action failed: {}", error.getMessage());
        return switch (error) {
            case NotFoundError e -> ResponseEntity.notFound().build();
            case ConflictError e -> ResponseEntity.status(409).body(e.getMessage());
            case ValidationError e -> ResponseEntity.badRequest().body(e.getMessage());
            default -> {
                log.error("Unexpected error: {}", error);
                yield ResponseEntity.internalServerError().body(error.getMessage());
            }
        };
    }
}
