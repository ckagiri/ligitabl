package com.ligitabl.api.usecases.team.deleteteam;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.ligitabl.api.shared.exceptions.UseCaseException;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/teams")
@RequiredArgsConstructor
public class DeleteTeamController {
    private final DeleteTeamUseCase deleteTeamUseCase;

    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteTeam(@PathVariable String id) {
        var command = DeleteTeamCommand.of(id);
        var result = deleteTeamUseCase.execute(command);

        return result.fold(
                error -> {
                    throw new UseCaseException(error);
                },
                _unit -> ResponseEntity.noContent().build());
    }
}
