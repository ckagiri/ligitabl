package com.ligitabl.api.usecases.season.getseasons;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.ligitabl.api.shared.exceptions.UseCaseException;
import com.ligitabl.api.usecases.season.SeasonDto;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@RestController
@RequestMapping("/api/seasons")
@RequiredArgsConstructor
@Slf4j
public class GetSeasonsController {

    private final GetSeasonsUseCase getSeasonsUseCase;

    @GetMapping
    public ResponseEntity<List<SeasonDto>> getSeasons() {
        log.info("GetSeasons request");
        var result = getSeasonsUseCase.execute();

        return result.fold(
                error -> {
                    throw new UseCaseException(error);
                },
                list -> ResponseEntity.ok(list));
    }
}
