package com.ligitabl.api.rest.round.advanceround;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.ligitabl.api.shared.errors.ConflictError;
import com.ligitabl.api.shared.errors.NotFoundError;
import com.ligitabl.api.shared.errors.UseCaseError;
import com.ligitabl.api.shared.errors.ValidationError;

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
        return advanceNowUseCase.execute().fold(
                this::toErrorResponse,
                ResponseEntity::ok);
    }

    /**
     * GET /api/admin/rounds/current/advancement-status
     *
     * Check the current round's advancement state.
     */
    @GetMapping("/current/advancement-status")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> getAdvancementStatus() {
        return statusUseCase.execute().fold(
                this::toErrorResponse,
                ResponseEntity::ok);
    }

    /**
     * DELETE /api/admin/rounds/current/cancel-advancement
     *
     * Cancel the pending auto-advance. Round stays FINALIZED; use advance-now when ready.
     */
    @DeleteMapping("/current/cancel-advancement")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> cancelAdvancement() {
        return cancelUseCase.execute().fold(
                this::toErrorResponse,
                ResponseEntity::ok);
    }

    private ResponseEntity<?> toErrorResponse(UseCaseError error) {
        return switch (error) {
            case NotFoundError e -> ResponseEntity.notFound().build();
            case ConflictError e -> ResponseEntity.status(409).body(new ErrorDto(e.getMessage()));
            case ValidationError e -> ResponseEntity.badRequest().body(new ErrorDto(e.getMessage()));
            default -> {
                log.error("Unexpected error in RoundAdvancementController: {}", error);
                yield ResponseEntity.internalServerError().body(new ErrorDto("Unexpected error"));
            }
        };
    }

    record ErrorDto(String message) {}
}
