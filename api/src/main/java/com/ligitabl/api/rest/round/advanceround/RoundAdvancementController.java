package com.ligitabl.api.rest.round.advanceround;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.ligitabl.api.scheduling.advanceround.RoundAdvancementService;
import com.ligitabl.model.repo.RoundRepo;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@RestController
@RequestMapping("/api/admin/rounds")
@RequiredArgsConstructor
@Slf4j
public class RoundAdvancementController {

    private final RoundAdvancementService advancementService;
    private final RoundRepo roundRepo;

    /**
     * POST /api/admin/rounds/{roundId}/advance-now
     *
     * Manually advance to next round immediately, skipping the delay window.
     */
    @PostMapping("/{roundId}/advance-now")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> advanceNow(@PathVariable UUID roundId) {
        try {
            boolean advanced = advancementService.advanceManually(roundId);

            if (!advanced) {
                return ResponseEntity.badRequest()
                        .body(new ErrorResponse("Round already advanced"));
            }

            var round = roundRepo.findById(roundId)
                    .orElseThrow(() -> new IllegalStateException("Round not found after advancement"));

            return ResponseEntity.ok(new AdvanceResponse(
                    roundId,
                    round.getPosition(),
                    round.getPosition() + 1,
                    "Manual",
                    round.getAdvancedAt()));

        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        } catch (IllegalStateException e) {
            return ResponseEntity.badRequest().body(new ErrorResponse(e.getMessage()));
        } catch (Exception e) {
            log.error("Unexpected error advancing round={}", roundId, e);
            return ResponseEntity.internalServerError()
                    .body(new ErrorResponse("Failed to advance round: " + e.getMessage()));
        }
    }

    /**
     * GET /api/admin/rounds/{roundId}/advancement-status
     *
     * Check current advancement state of a round.
     */
    @GetMapping("/{roundId}/advancement-status")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> getAdvancementStatus(@PathVariable UUID roundId) {
        var round = roundRepo.findById(roundId).orElse(null);

        if (round == null) {
            return ResponseEntity.notFound().build();
        }

        boolean scheduled = round.getAdvanceAt() != null && !round.isAdvanced();

        Integer minutesRemaining = null;
        if (scheduled) {
            var now = OffsetDateTime.now();
            if (round.getAdvanceAt().isAfter(now)) {
                minutesRemaining = (int) Duration.between(now, round.getAdvanceAt()).toMinutes();
            } else {
                minutesRemaining = 0;
            }
        }

        return ResponseEntity.ok(new AdvancementStatusResponse(
                roundId,
                round.getPosition(),
                round.computeStatus(null).toString(),
                scheduled,
                round.getAdvanceAt(),
                minutesRemaining,
                round.isAdvanced(),
                !round.isAdvanced()));
    }

    /**
     * DELETE /api/admin/rounds/{roundId}/cancel-advancement
     *
     * Cancel the pending auto-advance. Round stays FINALIZED.
     * Use advance-now to advance manually when ready.
     */
    @DeleteMapping("/{roundId}/cancel-advancement")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> cancelAdvancement(@PathVariable UUID roundId) {
        try {
            advancementService.cancelScheduledAdvancement(roundId);

            return ResponseEntity.ok(new CancelResponse(
                    roundId,
                    "Automatic advancement cancelled. Round remains FINALIZED. Use advance-now when ready."));

        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        } catch (IllegalStateException e) {
            return ResponseEntity.badRequest().body(new ErrorResponse(e.getMessage()));
        }
    }

    record ErrorResponse(String message) {}

    record AdvanceResponse(
            UUID roundId,
            int fromPosition,
            int toPosition,
            String method,
            OffsetDateTime advancedAt) {}

    record AdvancementStatusResponse(
            UUID roundId,
            int position,
            String status,
            boolean scheduled,
            OffsetDateTime advanceAt,
            Integer minutesRemaining,
            boolean advanced,
            boolean canOverride) {}

    record CancelResponse(
            UUID roundId,
            String message) {}
}
