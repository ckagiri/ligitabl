package com.ligitabl.api.usecases.contest.getconteststatus;

import com.ligitabl.api.auth.CurrentUserId;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;// Controller endpoint
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/contest")
@RequiredArgsConstructor
@Slf4j
public class GetContestStatusController {
    private final GetContestStatusUseCase getContestStatusUseCase;
        private final CurrentUserId currentUserId;

    @GetMapping("/status")
        public ResponseEntity<?> getContestStatus() {
                UUID userId = currentUserId.require();
                var status = getContestStatusUseCase.execute(userId);

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
