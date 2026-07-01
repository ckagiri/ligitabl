package com.ligitabl.api.web.rounds;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;

import com.ligitabl.api.config.CompetitionDefaults;
import com.ligitabl.api.rest.round.finalizeround.FinalizeRoundCommand;
import com.ligitabl.api.rest.round.finalizeround.FinalizeRoundError;
import com.ligitabl.api.rest.round.finalizeround.FinalizeRoundUseCase;
import com.ligitabl.model.domain.Season;
import com.ligitabl.model.repo.RoundRepo;
import com.ligitabl.model.repo.SeasonRepo;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Controller("webRefinalizeRoundController")
@RequestMapping("/rounds")
@RequiredArgsConstructor
@Slf4j
public class RefinalizeRoundController {

    private final SeasonRepo seasonRepo;
    private final RoundRepo roundRepo;
    private final CompetitionDefaults competitionDefaults;
    private final FinalizeRoundUseCase finalizeRoundUseCase;

    /**
     * POST /rounds/{roundPosition}/refinalize — setup-mode-only recompute of a past (or current)
     * round. Never advances the season's current round pointer.
     */
    @PostMapping("/{roundPosition}/refinalize")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    @ResponseBody
    public ResponseEntity<?> refinalize(@PathVariable int roundPosition) {
        log.info("POST /rounds/{}/refinalize", roundPosition);

        Season season = seasonRepo
                .findActiveSeason(competitionDefaults.defaultCompetitionSlug())
                .orElseThrow(() -> new IllegalStateException("No active season"));

        var result = finalizeRoundUseCase.execute(FinalizeRoundCommand.refinalize(season.getId(), roundPosition));

        return result.fold(
                error -> {
                    log.warn("Refinalize failed: {}", error);
                    return switch (error) {
                        case FinalizeRoundError.NotInSetupMode e -> ResponseEntity.status(409).build();
                        case FinalizeRoundError.RoundAheadOfCurrent e -> ResponseEntity.badRequest()
                                .build();
                        case FinalizeRoundError.RoundObstructed e -> ResponseEntity.status(409)
                                .build();
                        case FinalizeRoundError.RoundNotReady e -> ResponseEntity.badRequest()
                                .build();
                        case FinalizeRoundError.AlreadyFinalized e -> ResponseEntity.status(409)
                                .build();
                        case FinalizeRoundError.RoundNotFound e -> ResponseEntity.notFound()
                                .build();
                        case FinalizeRoundError.SeasonNotFound e -> ResponseEntity.notFound()
                                .build();
                        default -> ResponseEntity.internalServerError().build();
                    };
                },
                success -> ResponseEntity.ok()
                        .header("HX-Redirect", nextRoundUrl(season, roundPosition))
                        .build());
    }

    private String nextRoundUrl(Season season, int refinalizedPosition) {
        int currentPosition = roundRepo
                .findById(season.getCurrentRoundId())
                .map(round -> round.getPosition())
                .orElse(refinalizedPosition);

        int next = refinalizedPosition + 1;
        return next == currentPosition ? "/rounds/current/matches" : "/rounds/" + next + "/matches";
    }
}
