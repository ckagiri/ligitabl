package com.ligitabl.api.usecases.team.deleteteam;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.ligitabl.api.shared.exceptions.UseCaseException;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@RestController
@RequestMapping("/api/teams")
@RequiredArgsConstructor
@Slf4j
public class DeleteTeamController {
    private final DeleteTeamPort deleteTeamUseCase;

    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteTeam(@PathVariable String id) {
        var command = DeleteTeamCommand.of(id);
        log.info("DeleteTeam request id={}", id);
        var result = deleteTeamUseCase.execute(command);

        return result.fold(
                error -> {
                    throw new UseCaseException(error);
                },
                _unit -> {
                    log.info("Team deleted id={}", id);
                    return ResponseEntity.noContent().build();
                });
    }
}
