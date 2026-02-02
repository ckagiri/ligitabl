package com.ligitabl.api.rest.matchadmin;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.ligitabl.api.shared.exceptions.UseCaseException;
import com.ligitabl.api.rest.matchadmin.reschedulematch.RescheduleMatchCommand;
import com.ligitabl.api.rest.matchadmin.reschedulematch.RescheduleMatchUseCase;
import com.ligitabl.api.rest.matchadmin.reschedulematch.RescheduleResult;
import com.ligitabl.api.rest.matchadmin.transitionmatchstatus.TransitionMatchCommand;
import com.ligitabl.api.rest.matchadmin.transitionmatchstatus.TransitionMatchStatusUseCase;
import com.ligitabl.api.rest.matchadmin.transitionmatchstatus.TransitionResult;
import com.ligitabl.api.rest.shared.RoundPosition;
import com.ligitabl.model.domain.MatchStatus;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@RestController
@RequestMapping("/api/admin/rounds")
@RequiredArgsConstructor
@Slf4j
public class MatchAdminController {

    private final TransitionMatchStatusUseCase transitionUseCase;
    private final RescheduleMatchUseCase rescheduleUseCase;
    private final GetMatchAdminDetailsUseCase detailsUseCase;

    /**
     * POST /api/admin/rounds/current/matches/{matchSlug}/transition
     * POST /api/admin/rounds/{position}/matches/{matchSlug}/transition
     */
    @PostMapping("/{position}/matches/{matchSlug}/transition")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<TransitionResult> transitionMatch(
            @PathVariable String position,
            @PathVariable String matchSlug,
            @Valid @RequestBody TransitionRequest request,
            @RequestParam(required = false) String competition) {

        log.info("Transition match: position={}, slug={}, newStatus={}", position, matchSlug, request.newStatus());

        var cmd = TransitionMatchCommand.builder()
                .competitionIdentifier(competition)
                .roundPosition(RoundPosition.parse(position))
                .matchSlug(matchSlug)
                .newStatus(request.newStatus())
                .reason(request.reason())
                .score(
                        request.score() == null
                                ? null
                                : new TransitionMatchCommand.ScoreDto(
                                        request.score().homeGoals(),
                                        request.score().awayGoals()))
                .build();

        return transitionUseCase
                .execute(cmd)
                .fold(
                        err -> {
                            throw new UseCaseException(err);
                        },
                        ResponseEntity::ok);
    }

    /**
     * POST /api/admin/rounds/current/matches/{matchSlug}/reschedule
     * POST /api/admin/rounds/{position}/matches/{matchSlug}/reschedule
     */
    @PostMapping("/{position}/matches/{matchSlug}/reschedule")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<RescheduleResult> rescheduleMatch(
            @PathVariable String position,
            @PathVariable String matchSlug,
            @Valid @RequestBody RescheduleRequest request,
            @RequestParam(required = false) String competition) {

        log.info("Reschedule match: position={}, slug={}, toRound={}", position, matchSlug, request.newRoundPosition());

        var cmd = RescheduleMatchCommand.builder()
                .competitionIdentifier(competition)
                .roundPosition(RoundPosition.parse(position))
                .matchSlug(matchSlug)
                .newRoundPosition(request.newRoundPosition())
                .reason(request.reason())
                .build();

        return rescheduleUseCase
                .execute(cmd)
                .fold(
                        err -> {
                            throw new UseCaseException(err);
                        },
                        ResponseEntity::ok);
    }

    /**
     * GET /api/admin/rounds/current/matches/{matchSlug}
     * GET /api/admin/rounds/{position}/matches/{matchSlug}
     */
    @GetMapping("/{position}/matches/{matchSlug}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<MatchAdminDetailsDto> getMatchDetails(
            @PathVariable String position,
            @PathVariable String matchSlug,
            @RequestParam(required = false) String competition) {

        var query = new GetMatchAdminDetailsUseCase.Query(competition, RoundPosition.parse(position), matchSlug);

        return detailsUseCase
                .execute(query)
                .fold(
                        err -> {
                            throw new UseCaseException(err);
                        },
                        ResponseEntity::ok);
    }

    public record TransitionRequest(@NotNull MatchStatus newStatus, @NotBlank String reason, ScoreDto score) {
        public record ScoreDto(@Min(0) int homeGoals, @Min(0) int awayGoals) {}
    }

    public record RescheduleRequest(@Min(1) int newRoundPosition, @NotBlank String reason) {}
}
