package com.ligitabl.api.rest.team.getteambyid;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import com.ligitabl.api.rest.team.TeamDto;
import com.ligitabl.api.shared.exceptions.UseCaseException;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@RestController
@RequestMapping("/api/teams")
@RequiredArgsConstructor
@Slf4j
public class GetTeamByIdController {
    private final GetTeamByIdUseCase getTeamByIdUseCase;

    @GetMapping(params = "id")
    public ResponseEntity<TeamDto> getById(@RequestParam("id") String id) {
        log.info("GetTeamById request id={}", id);
        var query = new GetTeamByIdQuery(id);
        var result = getTeamByIdUseCase.execute(query);

        return result.fold(
                error -> {
                    throw new UseCaseException(error);
                },
                dto -> {
                    log.debug("GetTeamById success id={}", id);
                    return ResponseEntity.ok(dto);
                });
    }
}
