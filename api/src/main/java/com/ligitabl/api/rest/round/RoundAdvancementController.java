package com.ligitabl.api.rest.round;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.ligitabl.api.shared.errors.UnexpectedError;
import com.ligitabl.api.shared.errors.UseCaseError;
import com.ligitabl.api.shared.exceptions.UseCaseErrorStatus;
import com.ligitabl.api.usecase.round.advanceround.AdvanceCurrentRoundNowUseCase;
import com.ligitabl.api.usecase.round.advanceround.CancelRoundAdvancementUseCase;
import com.ligitabl.api.usecase.round.advanceround.GetRoundAdvancementStatusUseCase;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@RestController
@RequestMapping("/api/admin/rounds")
@RequiredArgsConstructor
@Slf4j
public class RoundAdvancementController {

    private final AdvanceCurrentRoundNowUseCase advanceNowUseCase;
    private final GetRoundAdvancementStatusUseCase statusUseCase;
    private final CancelRoundAdvancementUseCase cancelUseCase;

    /**
     * POST /api/admin/rounds/current/advance-now
     *
     * Manually advance the current round immediately, skipping the delay window.
     */
    @PostMapping("/current/advance-now")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> advanceNow() {
        return advanceNowUseCase.execute().fold(this::toErrorResponse, ResponseEntity::ok);
    }

    /**
     * GET /api/admin/rounds/current/advancement-status
     *
     * Check the current round's advancement state.
     */
    @GetMapping("/current/advancement-status")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> getAdvancementStatus() {
        return statusUseCase.execute().fold(this::toErrorResponse, ResponseEntity::ok);
    }

    /**
     * DELETE /api/admin/rounds/current/cancel-advancement
     *
     * Cancel the pending auto-advance. Round stays FINALIZED; use advance-now when ready.
     */
    @DeleteMapping("/current/cancel-advancement")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> cancelAdvancement() {
        return cancelUseCase.execute().fold(this::toErrorResponse, ResponseEntity::ok);
    }

    private ResponseEntity<?> toErrorResponse(UseCaseError error) {
        if (error instanceof UnexpectedError unexpected) {
            log.error("Round advancement action failed: {}", unexpected.logMessage(), unexpected.cause());
        }
        return ResponseEntity.status(UseCaseErrorStatus.of(error)).body(new ErrorDto(error.getMessage()));
    }

    record ErrorDto(String message) {}
}
