package com.ligitabl.api.web.rounds;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;

import com.ligitabl.api.config.CompetitionDefaults;
import com.ligitabl.api.rest.round.advanceround.AdvanceCurrentRoundNowUseCase;
import com.ligitabl.api.rest.round.finalizeround.FinalizeRoundCommand;
import com.ligitabl.api.rest.round.finalizeround.FinalizeRoundError;
import com.ligitabl.api.rest.round.finalizeround.FinalizeRoundUseCase;
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

    @PostMapping("/current/finalize-and-advance")
    @PreAuthorize("hasRole('ADMIN')")
    @ResponseBody
    public ResponseEntity<?> finalizeAndAdvance() {
        log.info("POST /rounds/current/finalize-and-advance");

        var season = seasonRepo
                .findActiveSeason(competitionDefaults.defaultCompetitionSlug())
                .orElseThrow(() -> new IllegalStateException("No active season"));

        var finalizeResult = finalizeRoundUseCase.execute(new FinalizeRoundCommand(season.getId(), false));

        if (finalizeResult.isLeft()) {
            var error = finalizeResult.getLeft();
            log.error("Finalize failed: {}", error);
            return switch (error) {
                case FinalizeRoundError.RoundObstructed e -> ResponseEntity.status(409).build();
                case FinalizeRoundError.RoundNotReady e -> ResponseEntity.badRequest().build();
                default -> ResponseEntity.internalServerError().build();
            };
        }

        var advanceResult = advanceCurrentRoundNowUseCase.execute();
        if (advanceResult.isLeft()) {
            log.warn("Finalization succeeded but advancement failed: {}", advanceResult.getLeft());
        }

        return ResponseEntity.ok()
                .header("HX-Redirect", "/rounds/current/matches")
                .build();
    }
}
