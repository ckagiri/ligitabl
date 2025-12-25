package com.ligitabl.api.usecases.contest.getconteststatus;

import com.sun.security.auth.UserPrincipal;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;// Controller endpoint
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/contest")
@RequiredArgsConstructor
@Slf4j
public class GetContestStatusController {
    private final GetContestStatusUseCase getContestStatusUseCase;

    @GetMapping("/status")
    public ResponseEntity<?> getContestStatus(@AuthenticationPrincipal UserPrincipal user) {
        var status = getContestStatusUseCase.execute(user.getId());

        return ResponseEntity.ok(Map.of(
                "has_joined", status.hasJoined(),
                "season", status.currentSeason() != null ? Map.of(
                        "id", status.currentSeason().getId(),
                        "name", status.currentSeason().getName(),
                        "current_round", status.currentSeason().getCurrentRoundId()
                ) : null,
                "prediction", status.prediction() != null ? Map.of(
                        "id", status.prediction().getId(),
                        "at_round_number", status.prediction().getAtRoundNumber()
                ) : null,
                "entries_count", status.entries().size()
        ));
    }
}
