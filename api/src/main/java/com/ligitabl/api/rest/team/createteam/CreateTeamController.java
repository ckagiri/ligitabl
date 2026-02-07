package com.ligitabl.api.rest.team.createteam;

import java.net.URI;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
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
public class CreateTeamController {
    private final CreateTeamUseCase createTeamUseCase;

    @PostMapping
    public ResponseEntity<TeamDto> createTeam(@RequestBody CreateTeamCommand command) {
        log.info("CreateTeam request received slug={} tla={}", command.getSlug(), command.getTla());
        var result = createTeamUseCase.execute(command);

        return result.fold(
                error -> {
                    throw new UseCaseException(error);
                },
                dto -> {
                    log.info("Team created id={} slug={}", dto.getId(), dto.getSlug());
                    return ResponseEntity.created(URI.create("/api/teams/" + dto.getId()))
                            .body(dto);
                });
    }
}
