package com.ligitabl.api.usecases.team.updateteam;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import com.ligitabl.api.shared.exceptions.UseCaseException;
import com.ligitabl.api.usecases.team.TeamDto;
import com.ligitabl.api.usecases.team.TeamPayload;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/teams")
@RequiredArgsConstructor
public class UpdateTeamController {
    private final UpdateTeamPort updateTeamUseCase;

    @PutMapping("/{id}")
    public ResponseEntity<TeamDto> updateTeam(@PathVariable String id, @RequestBody TeamPayload payload) {
        var command = UpdateTeamCommand.of(id, payload);
        var result = updateTeamUseCase.execute(command);

        return result.fold(
                error -> {
                    throw new UseCaseException(error);
                },
                dto -> ResponseEntity.ok(dto));
    }
}
