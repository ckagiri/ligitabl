package com.ligitabl.api.web.predictions.roundopeningswap;

import java.security.Principal;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;

import com.ligitabl.api.auth.security.WebUserDetails;
import com.ligitabl.api.rest.prediction.makeswap.SwapCommand;
import com.ligitabl.api.rest.prediction.makeswap.SwapError;
import com.ligitabl.api.rest.prediction.roundopeningswap.RoundOpeningSwapCommand;
import com.ligitabl.api.rest.prediction.roundopeningswap.RoundOpeningSwapUseCase;
import com.ligitabl.api.web.shared.security.WebSecurity;

import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Controller
@Slf4j
@RequiredArgsConstructor
@RequestMapping("/seasonprediction")
public class RoundOpeningSwapController {

    private final RoundOpeningSwapUseCase roundOpeningSwapUseCase;

    @PostMapping("/opening-swaps")
    @ResponseBody
    public Map<String, Object> openingSwaps(
            @RequestBody RoundOpeningSwapRequest request, Principal principal, HttpServletResponse response) {
        WebUserDetails userDetails = WebSecurity.resolveUser(principal);
        if (userDetails == null) {
            response.setStatus(401);
            return Map.of("success", false, "message", "Authentication required");
        }

        int swapCount = request.swaps() == null ? 0 : request.swaps().size();
        log.info("POST /seasonprediction/opening-swaps - user: {}, swaps: {}", userDetails.getEmail(), swapCount);

        List<SwapCommand> swapCommands = request.swaps() == null
                ? List.of()
                : request.swaps().stream()
                        .map(e -> new SwapCommand(e.teamACode(), e.teamBCode()))
                        .toList();

        return roundOpeningSwapUseCase
                .execute(userDetails.getUserId(), new RoundOpeningSwapCommand(swapCommands))
                .fold(
                        error -> {
                            response.setStatus(toHttpStatus(error));
                            log.warn("Opening swap failed: {}", error);
                            return Map.of("success", false, "message", errorMessage(error));
                        },
                        success -> {
                            log.info("Opening swaps committed: {} swap(s)", success.swapsApplied());
                            return Map.of("success", true, "message", "Prediction updated successfully");
                        });
    }

    private int toHttpStatus(SwapError error) {
        return switch (error) {
            case SwapError.NoPredictionFound __ -> 404;
            case SwapError.RoundNotFound __ -> 404;
            case SwapError.RoundNotOpen __ -> 409;
            case SwapError.OpeningAlreadyUsed __ -> 409;
            case SwapError.PreSeasonMergeRequired __ -> 409;
            case SwapError.SeasonCompleted __ -> 409;
            case SwapError.SeasonNotFound __ -> 404;
            case SwapError.SeasonNotInPlay __ -> 409;
            case SwapError.SeasonInSetupMode __ -> 409;
            case SwapError.BatchSizeInvalid __ -> 400;
            case SwapError.InvalidTeamCode __ -> 400;
            case SwapError.TeamsNotFound __ -> 400;
            case SwapError.UseOpeningWindowFirst __ -> 500;
            case SwapError.CooldownActive __ -> 500;
        };
    }

    private String errorMessage(SwapError error) {
        return switch (error) {
            case SwapError.NoPredictionFound __ -> "No prediction found for current season";
            case SwapError.RoundNotFound e -> "Round " + e.roundPosition() + " not found";
            case SwapError.RoundNotOpen e -> "Cannot swap when round is " + e.roundStatus();
            case SwapError.OpeningAlreadyUsed e -> "Opening swaps already used for round " + e.round();
            case SwapError.PreSeasonMergeRequired __ -> "Submit your table for the season before swapping";
            case SwapError.SeasonCompleted __ -> "Cannot swap in completed season";
            case SwapError.SeasonNotFound __ -> "No active season found";
            case SwapError.SeasonNotInPlay __ -> "Cannot swap when the season is not in play";
            case SwapError.SeasonInSetupMode __ -> "Season is being reconfigured. Please try again shortly.";
            case SwapError.BatchSizeInvalid e -> "Opening swaps must be between 1 and 2, got " + e.size();
            case SwapError.InvalidTeamCode e -> "Invalid team code: " + e.code();
            case SwapError.TeamsNotFound e -> "Teams not found in your prediction: " + e.teamACode() + ", "
                    + e.teamBCode();
            case SwapError.UseOpeningWindowFirst __ -> "Something went wrong";
            case SwapError.CooldownActive __ -> "Something went wrong";
        };
    }
}
