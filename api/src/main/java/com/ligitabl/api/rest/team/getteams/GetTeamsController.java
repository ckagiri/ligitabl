package com.ligitabl.api.rest.team.getteams;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.ligitabl.api.rest.team.TeamDto;
import com.ligitabl.api.shared.exceptions.UseCaseException;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@RestController
@RequestMapping("/api/teams")
@RequiredArgsConstructor
@Slf4j
public class GetTeamsController {
    private final GetTeamsUseCase getTeamsUseCase;

    @GetMapping
    public ResponseEntity<List<TeamDto>> getTeams() {
        log.info("GetTeams request");
        var result = getTeamsUseCase.execute();

        return result.fold(
                error -> {
                    throw new UseCaseException(error);
                },
                list -> ResponseEntity.ok(list));
    }
}
