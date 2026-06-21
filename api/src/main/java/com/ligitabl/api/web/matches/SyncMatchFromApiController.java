package com.ligitabl.api.web.matches;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;

import com.ligitabl.api.rest.match.syncfromapi.SyncMatchUseCase;
import com.ligitabl.api.rest.match.syncfromapi.SyncMatchUseCase.SyncMatchCommand;
import com.ligitabl.api.rest.match.syncfromapi.SyncMatchUseCase.SyncMatchError;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Controller("webSyncMatchFromApiController")
@RequestMapping("/matches")
@RequiredArgsConstructor
@Slf4j
public class SyncMatchFromApiController {

    private final SyncMatchUseCase syncMatchUseCase;

    @PostMapping("/{clientId}/sync-from-api")
    @PreAuthorize("hasRole('ADMIN')")
    @ResponseBody
    public ResponseEntity<?> syncMatch(@PathVariable int clientId) {
        log.info("POST /matches/{}/sync-from-api", clientId);

        var result = syncMatchUseCase.execute(new SyncMatchCommand(clientId));

        return result.fold(
                error -> switch (error) {
                    case SyncMatchError.NotFound e -> {
                        log.warn("Match not found for clientId={}", e.clientId());
                        yield ResponseEntity.notFound().build();
                    }
                    case SyncMatchError.AlreadyFinished e -> {
                        log.info("Match clientId={} already finished, skipping sync", e.clientId());
                        yield ResponseEntity.status(409).build();
                    }
                    case SyncMatchError.ApiError e -> {
                        log.error("API error syncing match clientId={}: {}", clientId, e.cause());
                        yield ResponseEntity.status(502).build();
                    }
                },
                __ -> ResponseEntity.noContent()
                        .header("HX-Trigger", "matchSyncComplete")
                        .build());
    }
}
