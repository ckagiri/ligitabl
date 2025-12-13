package com.ligitabl.api.usecases.match.getdefaultroundmatches;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.ligitabl.api.shared.exceptions.UseCaseException;
import com.ligitabl.api.usecases.match.MatchDto;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@RestController
@RequestMapping("/api/rounds")
@RequiredArgsConstructor
@Slf4j
public class GetDefaultRoundMatchesController {

    private final GetDefaultRoundMatchesUseCase getDefaultRoundMatchesUseCase;

    @GetMapping("/default/matches")
    public ResponseEntity<List<MatchDto>> getDefaultRoundMatches() {
        log.info("GetDefaultRoundMatches request");
        var result = getDefaultRoundMatchesUseCase.execute();

        return result.fold(
                error -> {
                    throw new UseCaseException(error);
                },
                matches -> ResponseEntity.ok(matches));
    }
}
