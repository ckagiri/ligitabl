package com.ligitabl.api.rest.team.updateteam;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import com.ligitabl.api.shared.exceptions.UseCaseException;
import com.ligitabl.api.rest.team.TeamDto;
import com.ligitabl.api.rest.team.TeamPayload;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@RestController
@RequestMapping("/api/teams")
@RequiredArgsConstructor
@Slf4j
public class UpdateTeamController {
    private final UpdateTeamUseCase updateTeamUseCase;

    @PutMapping("/{id}")
    public ResponseEntity<TeamDto> updateTeam(@PathVariable String id, @RequestBody TeamPayload payload) {
        var command = UpdateTeamCommand.of(id, payload);
        log.info("UpdateTeam request id={} slug={} tla={}", id, payload.getSlug(), payload.getTla());
        var result = updateTeamUseCase.execute(command);

        return result.fold(
                error -> {
                    throw new UseCaseException(error);
                },
                dto -> {
                    log.info("Team updated id={} slug={}", dto.getId(), dto.getSlug());
                    return ResponseEntity.ok(dto);
                });
    }
}
