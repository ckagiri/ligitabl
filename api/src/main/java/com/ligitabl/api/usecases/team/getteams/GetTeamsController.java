package com.ligitabl.api.usecases.team.getteams;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.ligitabl.api.usecases.team.TeamDto;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@RestController
@RequestMapping("/api/teams")
@RequiredArgsConstructor
@Slf4j
public class GetTeamsController {
    private final GetTeamsPort getTeamsUseCase;

    @GetMapping
    public List<TeamDto> getTeams() {
        log.debug("List teams request");
        return getTeamsUseCase.execute();
    }
}
