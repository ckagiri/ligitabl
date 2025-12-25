package com.ligitabl.api.usecases.contest.getconteststatus;

import com.ligitabl.model.auth.PublicId;
import com.ligitabl.model.repo.UserRepo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;// Controller endpoint
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/contest")
@RequiredArgsConstructor
@Slf4j
public class GetContestStatusController {
    private final GetContestStatusUseCase getContestStatusUseCase;
        private final UserRepo userRepo;

    @GetMapping("/status")
        public ResponseEntity<?> getContestStatus(Authentication authentication) {
                UUID userId = resolveUserId(authentication);
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

        private UUID resolveUserId(Authentication authentication) {
                String publicIdStr = authentication.getName();
                PublicId publicId = PublicId.create(publicIdStr);

                return userRepo.findByPublicId(publicId)
                                .map(com.ligitabl.model.domain.User::getId)
                                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "User not found"));
        }
}
