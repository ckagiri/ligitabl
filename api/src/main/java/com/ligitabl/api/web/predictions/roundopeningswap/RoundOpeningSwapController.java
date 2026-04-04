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
import com.ligitabl.api.rest.prediction.roundopeningswap.RoundOpeningSwapCommand;
import com.ligitabl.api.rest.prediction.roundopeningswap.RoundOpeningSwapError;
import com.ligitabl.api.rest.prediction.roundopeningswap.RoundOpeningSwapResult;
import com.ligitabl.api.rest.prediction.roundopeningswap.RoundOpeningSwapUseCase;
import com.ligitabl.api.shared.Either;
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

        Either<RoundOpeningSwapError, RoundOpeningSwapResult> result =
                roundOpeningSwapUseCase.execute(userDetails.getUserId(), new RoundOpeningSwapCommand(swapCommands));

        return result.fold(
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

    private int toHttpStatus(RoundOpeningSwapError error) {
        return switch (error) {
            case RoundOpeningSwapError.NoPredictionFound __ -> 404;
            case RoundOpeningSwapError.CurrentRoundNotFound __ -> 404;
            case RoundOpeningSwapError.RoundNotOpen __ -> 409;
            case RoundOpeningSwapError.OpeningAlreadyUsed __ -> 409;
            case RoundOpeningSwapError.BatchSizeInvalid __ -> 400;
            case RoundOpeningSwapError.InvalidTeamCode __ -> 400;
            case RoundOpeningSwapError.TeamsNotFound __ -> 400;
            case RoundOpeningSwapError.SeasonCompleted __ -> 409;
        };
    }

    private String errorMessage(RoundOpeningSwapError error) {
        return switch (error) {
            case RoundOpeningSwapError.NoPredictionFound __ -> "No prediction found for current season";
            case RoundOpeningSwapError.CurrentRoundNotFound __ -> "Current round not found";
            case RoundOpeningSwapError.RoundNotOpen e -> "Cannot swap when round is " + e.roundStatus();
            case RoundOpeningSwapError.OpeningAlreadyUsed e -> "Opening swaps already used for round " + e.round();
            case RoundOpeningSwapError.BatchSizeInvalid e -> "Opening swaps must be between 1 and 5, got " + e.size();
            case RoundOpeningSwapError.InvalidTeamCode e -> "Invalid team code: " + e.code();
            case RoundOpeningSwapError.TeamsNotFound e -> "Teams not found in your prediction: " + e.teamACode() + ", "
                    + e.teamBCode();
            case RoundOpeningSwapError.SeasonCompleted __ -> "Cannot swap in completed season";
        };
    }
}
