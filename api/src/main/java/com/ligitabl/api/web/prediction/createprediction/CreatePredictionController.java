package com.ligitabl.api.web.prediction.createprediction;

import com.ligitabl.api.auth.security.WebUserDetails;
import com.ligitabl.api.shared.Either;
import com.ligitabl.api.rest.prediction.createprediction.CreatePredictionCommand;
import com.ligitabl.api.rest.prediction.createprediction.CreatePredictionError;
import com.ligitabl.api.rest.prediction.createprediction.CreatePredictionResult;
import com.ligitabl.api.rest.prediction.createprediction.CreatePredictionUseCase;
import com.ligitabl.api.rest.prediction.createprediction.TeamRankDto;
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
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;

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
            Principal principal,
            HttpServletResponse response
    ) {
        WebUserDetails userDetails = WebSecurity.resolveUser(principal);
        if (userDetails == null) {
            response.setStatus(401);
            return Map.of("success", false, "message", "Authentication required");
        }

        log.info("POST /seasonprediction - user: {}, teams: {}",
            userDetails.getEmail(), request.teamCodes().size());

        var teamRankings = toRankings(request.teamCodes());
        CreatePredictionCommand command = new CreatePredictionCommand(teamRankings);

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
                    return Map.of("success", true, "message", "Prediction created successfully");
                }
        );
    }

    List<TeamRankDto> toRankings(List<String> teamCodes) {
        return IntStream.range(0, teamCodes.size())
            .mapToObj(i -> TeamRankDto.of(teamCodes.get(i), i + 1))
                .toList();
    }

    public int toHttpStatus(CreatePredictionError error) {
        return switch (error) {
            case CreatePredictionError.NotFound __ -> 404;
            case CreatePredictionError.Completed __ -> 409;
            case CreatePredictionError.AlreadyJoined __ -> 409;
            case CreatePredictionError.InvalidTeamCount __ -> 400;
            case CreatePredictionError.DuplicatePositions __ -> 400;
            case CreatePredictionError.DuplicateTeamCodes __ -> 400;
            case CreatePredictionError.InvalidTeamCodes __ -> 400;
            case CreatePredictionError.Ended __ -> 409;
            case CreatePredictionError.DefaultContestNotFound __ -> 404;
            case CreatePredictionError.TransactionFailed __ -> 500;
        };
    }

    private String errorMessage(CreatePredictionError error) {
        return switch (error) {
            case CreatePredictionError.NotFound __ -> "No active season available";
            case CreatePredictionError.Completed __ -> "Cannot join a completed season";
            case CreatePredictionError.AlreadyJoined __ -> "You have already joined this season";
            case CreatePredictionError.InvalidTeamCount e ->
                    String.format("Expected %d teams, but received %d", e.required(), e.provided());
            case CreatePredictionError.DuplicatePositions __ -> "Each position must be unique";
            case CreatePredictionError.DuplicateTeamCodes __ -> "Each team can only appear once";
            case CreatePredictionError.InvalidTeamCodes __ -> "Some team codes are not valid for this season";
            case CreatePredictionError.Ended __ -> "Cannot join - season has ended";
            case CreatePredictionError.DefaultContestNotFound __ -> "Default contest not found";
            case CreatePredictionError.TransactionFailed e -> "Failed to create prediction: " + e.reason();
        };
    }

}
