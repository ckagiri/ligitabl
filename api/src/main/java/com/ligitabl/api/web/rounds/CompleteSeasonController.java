package com.ligitabl.api.web.rounds;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;

import com.ligitabl.api.rest.season.admin.CompleteSeasonUseCase;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Controller("webCompleteSeasonController")
@RequestMapping("/seasons")
@RequiredArgsConstructor
@Slf4j
public class CompleteSeasonController {

    private final CompleteSeasonUseCase completeSeasonUseCase;

    @PostMapping("/current/complete")
    @PreAuthorize("hasRole('ADMIN')")
    @ResponseBody
    public ResponseEntity<?> completeSeason() {
        log.info("POST /seasons/current/complete");

        var result = completeSeasonUseCase.execute();
        return switch (result) {
            case CompleteSeasonUseCase.Result.Ok ok -> ResponseEntity.ok()
                    .header("HX-Redirect", "/rounds/current/matches")
                    .build();
            case CompleteSeasonUseCase.Result.SeasonNotFound e -> {
                log.warn("Complete season failed: no active season found");
                yield ResponseEntity.notFound().build();
            }
            case CompleteSeasonUseCase.Result.SeasonNotEligible e -> {
                log.warn("Complete season rejected: {}", e.reason());
                yield ResponseEntity.badRequest().body(e.reason());
            }
        };
    }
}
