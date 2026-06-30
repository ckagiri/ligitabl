package com.ligitabl.api.web.predictions.createprediction;

import java.security.Principal;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import com.ligitabl.api.auth.security.WebUserDetails;
import com.ligitabl.api.rest.prediction.createprediction.CreatePredictionCommand;
import com.ligitabl.api.rest.prediction.createprediction.CreatePredictionError;
import com.ligitabl.api.rest.prediction.createprediction.CreatePredictionResult;
import com.ligitabl.api.rest.prediction.createprediction.CreatePredictionUseCase;
import com.ligitabl.api.shared.Either;
import com.ligitabl.api.web.shared.security.WebSecurity;

import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Controller("webCreatePredictionController")
@Slf4j
@RequiredArgsConstructor
@RequestMapping("/seasonprediction")
public class CreatePredictionController {
    private final CreatePredictionUseCase createPredictionUseCase;

    @PostMapping
    @ResponseBody
    public Map<String, Object> createSeasonPrediction(
            @RequestBody CreatePredictionRequest request,
            @RequestParam(name = "next", required = false) String nextUrl,
            Principal principal,
            HttpServletResponse response) {
        WebUserDetails userDetails = WebSecurity.resolveUser(principal);
        if (userDetails == null) {
            response.setStatus(401);
            return Map.of("success", false, "message", "Authentication required");
        }

        log.info(
                "POST /seasonprediction - user: {}, swaps: {}",
                userDetails.getEmail(),
                request.swaps() == null ? 0 : request.swaps().size());

        CreatePredictionCommand command = new CreatePredictionCommand(
                request.swaps() == null
                        ? List.of()
                        : request.swaps().stream()
                                .map(s -> new CreatePredictionCommand.SwapPair(s.teamACode(), s.teamBCode()))
                                .toList());

        Either<CreatePredictionError, CreatePredictionResult> result =
                createPredictionUseCase.execute(userDetails.getUserId(), command);

        return result.fold(
                error -> {
                    response.setStatus(toHttpStatus(error));
                    log.warn("Create prediction failed: {}", error);
                    return Map.of("success", false, "message", errorMessage(error));
                },
                created -> {
                    log.info("Created season prediction: {}", created.predictionId());
                    if (nextUrl != null && !nextUrl.isBlank()) {
                        return Map.of(
                                "success", true, "message", "Prediction created successfully", "nextUrl", nextUrl);
                    }
                    return Map.of("success", true, "message", "Prediction created successfully");
                });
    }

    public int toHttpStatus(CreatePredictionError error) {
        return switch (error) {
            case CreatePredictionError.NotFound __ -> 404;
            case CreatePredictionError.Completed __ -> 409;
            case CreatePredictionError.AlreadyJoined __ -> 409;
            case CreatePredictionError.TooManySwaps __ -> 400;
            case CreatePredictionError.SameTeam __ -> 400;
            case CreatePredictionError.InvalidTeamCode __ -> 400;
            case CreatePredictionError.Ended __ -> 409;
            case CreatePredictionError.CurrentRoundNotFound __ -> 404;
            case CreatePredictionError.MainContestNotFound __ -> 404;
            case CreatePredictionError.CorruptPreSeasonRegistration __ -> 500;
            case CreatePredictionError.TransactionFailed __ -> 500;
        };
    }

    private String errorMessage(CreatePredictionError error) {
        return switch (error) {
            case CreatePredictionError.NotFound __ -> "No active season available";
            case CreatePredictionError.Completed __ -> "Cannot join a completed season";
            case CreatePredictionError.AlreadyJoined __ -> "You have already joined this season";
            case CreatePredictionError.TooManySwaps e -> "Too many swaps: provided " + e.provided() + ", maximum is "
                    + e.max();
            case CreatePredictionError.SameTeam __ -> "You must swap two different teams";
            case CreatePredictionError.InvalidTeamCode e -> "Team code not valid for this season: " + e.code();
            case CreatePredictionError.Ended __ -> "Cannot join - season has ended";
            case CreatePredictionError.CurrentRoundNotFound __ -> "Current round not found";
            case CreatePredictionError.MainContestNotFound __ -> "Default contest not found";
            case CreatePredictionError.CorruptPreSeasonRegistration __ -> "Your pre-season registration is in an "
                    + "invalid state. Please contact support.";
            case CreatePredictionError.TransactionFailed e -> "Failed to create prediction: " + e.reason();
        };
    }
}
