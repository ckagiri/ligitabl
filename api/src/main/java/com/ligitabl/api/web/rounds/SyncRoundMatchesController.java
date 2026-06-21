package com.ligitabl.api.web.rounds;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;

import com.ligitabl.api.rest.match.syncfromapi.SyncRoundUseCase;
import com.ligitabl.api.rest.match.syncfromapi.SyncRoundUseCase.SyncRoundCommand;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Controller("webSyncRoundMatchesController")
@RequestMapping("/rounds")
@RequiredArgsConstructor
@Slf4j
public class SyncRoundMatchesController {

    private final SyncRoundUseCase syncRoundUseCase;

    @PostMapping("/current/sync-matches-from-api")
    @PreAuthorize("hasRole('ADMIN')")
    @ResponseBody
    public ResponseEntity<?> syncCurrentRound() {
        log.info("POST /rounds/current/sync-matches-from-api");

        var result = syncRoundUseCase.execute(new SyncRoundCommand());

        return result.fold(
                error -> {
                    log.error("Round sync failed: {}", error);
                    return ResponseEntity.status(502).build();
                },
                __ -> ResponseEntity.noContent()
                        .header("HX-Trigger", "matchSyncComplete")
                        .build());
    }
}
