package com.ligitabl.api.rest.competition.getcompetitions;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.ligitabl.api.rest.competition.CompetitionDto;
import com.ligitabl.api.shared.exceptions.UseCaseException;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@RestController
@RequestMapping("/api/competitions")
@RequiredArgsConstructor
@Slf4j
public class GetCompetitionsController {

    private final GetCompetitionsUseCase getCompetitionsUseCase;

    @GetMapping
    public ResponseEntity<List<CompetitionDto>> getCompetitions() {
        log.info("GetCompetitions request");
        var result = getCompetitionsUseCase.execute();

        return result.fold(
                error -> {
                    throw new UseCaseException(error);
                },
                list -> ResponseEntity.ok(list));
    }
}
