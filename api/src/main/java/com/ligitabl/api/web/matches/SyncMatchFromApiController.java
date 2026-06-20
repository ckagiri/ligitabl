package com.ligitabl.api.web.matches;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;

import com.ligitabl.api.rest.match.syncfromapi.SyncMatchFromApiUseCase;
import com.ligitabl.api.rest.match.syncfromapi.SyncMatchFromApiUseCase.SyncMatchFromApiCommand;
import com.ligitabl.api.rest.match.syncfromapi.SyncMatchFromApiUseCase.SyncMatchFromApiError;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Controller
@RequestMapping("/web/matches")
@RequiredArgsConstructor
@Slf4j
public class SyncMatchFromApiController {

    private final SyncMatchFromApiUseCase syncMatchFromApiUseCase;

    @PostMapping("/{clientId}/sync-from-api")
    @PreAuthorize("hasRole('ADMIN')")
    @ResponseBody
    public ResponseEntity<Void> syncMatch(@PathVariable int clientId) {
        log.info("POST /web/matches/{}/sync-from-api", clientId);

        var result = syncMatchFromApiUseCase.execute(new SyncMatchFromApiCommand(clientId));

        return result.fold(
                error -> switch (error) {
                    case SyncMatchFromApiError.NotFound e -> {
                        log.warn("Match not found for clientId={}", e.clientId());
                        yield ResponseEntity.<Void>notFound().build();
                    }
                    case SyncMatchFromApiError.AlreadyFinished e -> {
                        log.info("Match clientId={} already finished, skipping sync", e.clientId());
                        yield ResponseEntity.<Void>status(409).build();
                    }
                    case SyncMatchFromApiError.ApiError e -> {
                        log.error("API error syncing match clientId={}: {}", clientId, e.cause());
                        yield ResponseEntity.<Void>status(502).build();
                    }
                },
                __ -> ResponseEntity.<Void>noContent()
                        .header("HX-Trigger", "matchSyncComplete")
                        .build());
    }
}
