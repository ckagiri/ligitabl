package com.ligitabl.api.usecases.team.createteam;

import java.net.URI;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.ligitabl.api.shared.exceptions.BusinessFailureException;
import com.ligitabl.api.usecases.team.TeamDto;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/teams")
@RequiredArgsConstructor
public class CreateTeamController {
    private final CreateTeamUseCase createTeamUseCase;

    @PostMapping
    public ResponseEntity<TeamDto> createTeam(@RequestBody CreateTeamCommand command) {
        var result = createTeamUseCase.execute(command);

        return result.fold(error -> {
            throw new BusinessFailureException(error);
        }, dto -> ResponseEntity.created(URI.create("/api/teams/" + dto.getId())).body(dto));
    }
}
