package com.ligitabl.api.web;

import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import lombok.extern.slf4j.Slf4j;

@RestController
@RequestMapping("/api/status")
@Slf4j
public class StatusController {

    @GetMapping
    public ResponseEntity<Map<String, Object>> status() {
        // Keep noise low; DEBUG-level log for observability without cluttering INFO
        log.debug("Status endpoint called");
        return ResponseEntity.ok(
                Map.of("status", "ok", "service", "ligitabl", "timestamp", System.currentTimeMillis()));
    }
}
