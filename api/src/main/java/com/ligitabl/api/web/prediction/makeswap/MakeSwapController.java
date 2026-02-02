package com.ligitabl.api.web.prediction.makeswap;

import com.ligitabl.api.auth.security.WebUserDetails;
import com.ligitabl.api.shared.Either;
import com.ligitabl.api.rest.prediction.makeswap.MakeSwapUseCase;
import com.ligitabl.api.rest.prediction.makeswap.SwapCommand;
import com.ligitabl.api.rest.prediction.makeswap.SwapError;
import com.ligitabl.api.rest.prediction.makeswap.SwapResult;
import com.ligitabl.api.web.shared.security.WebSecurity;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;

import java.security.Principal;
import java.util.Map;

@Controller("webMakeSwapController")
@Slf4j
@RequiredArgsConstructor
@RequestMapping("/seasonprediction")
public class MakeSwapController {
    private final MakeSwapUseCase makeSwapUseCase;

    @PostMapping("/swap")
    @ResponseBody
    public Map<String, Object> swapTeams(
            @RequestBody MakeSwapRequest request,
            Principal principal,
            HttpServletResponse response
    ) {
        WebUserDetails userDetails = WebSecurity.resolveUser(principal);
        if (userDetails == null) {
            response.setStatus(401);
            return Map.of("success", false, "message", "Authentication required");
        }

        log.info("POST /seasonprediction/swap - user: {}, teamA: {}, teamB: {}",
            userDetails.getEmail(), request.teamACode(), request.teamBCode());

        SwapCommand command = new SwapCommand(request.teamACode(), request.teamBCode());

        Either<SwapError, SwapResult> result = makeSwapUseCase.execute(userDetails.getUserId(), command);

        return result.fold(
                error -> {
                    response.setStatus(toHttpStatus(error));
                    log.warn("Swap failed: {}", error);
                    return Map.of("success", false, "message", errorMessage(error));
                },
                updated -> {
                    log.info("Swapped teams successfully");
                    return Map.of("success", true, "message", "Prediction updated successfully");
                }
        );
    }

    /**
     * Map error type to HTTP status code.
     *
     * @param error the use case error
     * @return HTTP status code (400, 404, 409, 500)
     */
    public int toHttpStatus(SwapError error) {
        return switch (error) {
            case SwapError.NoPredictionFound __ -> 404;
            case SwapError.RoundNotFound __ -> 404;
            case SwapError.RoundNotOpen __ -> 409;
            case SwapError.CooldownActive __ -> 429;
            case SwapError.InvalidTeamCode __ -> 400;
            case SwapError.TeamsNotFound __ -> 400;
            case SwapError.SeasonCompleted __ -> 409;
        };
    }

    private String errorMessage(SwapError error) {
        return switch (error) {
            case SwapError.NoPredictionFound __ -> "No prediction found for current season";
            case SwapError.RoundNotFound e -> "Round " + e.roundPosition() + " not found";
            case SwapError.RoundNotOpen e -> "Cannot swap when round is " + e.roundStatus();
            case SwapError.CooldownActive e ->
                    String.format("Next swap available in %.1fh", e.hoursRemaining());
            case SwapError.InvalidTeamCode e -> "Invalid team code: " + e.code();
            case SwapError.TeamsNotFound e ->
                    "Teams not found in your prediction: " + e.teamACode() + ", " + e.teamBCode();
            case SwapError.SeasonCompleted __ -> "Cannot swap in completed season";
        };
    }

}
