package com.ligitabl.api.web.finaltable.savefinaltable;

import java.security.Principal;
import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseBody;

import com.ligitabl.api.auth.security.WebUserDetails;
import com.ligitabl.api.rest.finaltable.savefinaltable.SaveFinalTableCommand;
import com.ligitabl.api.rest.finaltable.savefinaltable.SaveFinalTablePredictionUseCase;
import com.ligitabl.api.rest.finaltable.shared.FinalTableError;
import com.ligitabl.api.web.shared.security.WebSecurity;
import com.ligitabl.model.domain.FinalTablePrediction;
import com.ligitabl.model.domain.TeamRank;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/** POST /final-table — saves a batch of swaps replayed server-side. */
@Controller
@RequiredArgsConstructor
@Slf4j
public class SaveFinalTableWebController {

    private final SaveFinalTablePredictionUseCase saveUseCase;

    @PostMapping("/final-table")
    @ResponseBody
    public ResponseEntity<?> save(@RequestBody SaveFinalTableCommand command, Principal principal) {
        WebUserDetails user = WebSecurity.resolveUser(principal);
        if (user == null) {
            return status(HttpStatus.UNAUTHORIZED, "Sign in to save your final table.");
        }

        return saveUseCase.execute(user.getUserId(), command).fold(this::toError, this::toSuccess);
    }

    private ResponseEntity<?> toSuccess(FinalTablePrediction saved) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("success", true);
        body.put("message", "Saved");
        body.put("swapCount", saved.getSwapCount());
        body.put("settledAt", saved.getSettledAt());
        // The client flips hasEntry on this, so an existing clean row disables Save without a reload.
        body.put("hasEntry", true);
        body.put(
                "order",
                TeamRank.inPositionOrder(saved.getRankings()).stream()
                        .map(TeamRank::getCode)
                        .toList());
        return ResponseEntity.ok(body);
    }

    private ResponseEntity<?> toError(FinalTableError error) {
        return switch (error) {
            case FinalTableError.SeasonNotFound e -> status(HttpStatus.NOT_FOUND, "No active season.");
            case FinalTableError.EntryClosed e -> status(
                    HttpStatus.FORBIDDEN, "The final table is locked for this season.");
            case FinalTableError.AlreadyScored e -> status(
                    HttpStatus.FORBIDDEN, "This table has been scored and can no longer change.");
                // 409, not 400: the request was well-formed, the row just moved underneath it.
            case FinalTableError.OutOfSync e -> status(
                    HttpStatus.CONFLICT, "This table changed in another tab. Reloading.");
            case FinalTableError.NothingToSave e -> status(HttpStatus.BAD_REQUEST, "No changes to save.");
            case FinalTableError.InvalidTeamCode e -> status(HttpStatus.BAD_REQUEST, "Unknown team: " + e.code());
            case FinalTableError.TeamsNotFound e -> status(HttpStatus.BAD_REQUEST, "Teams not found in your table.");
        };
    }

    private ResponseEntity<?> status(HttpStatus status, String message) {
        return ResponseEntity.status(status).body(Map.of("success", false, "message", message));
    }
}
