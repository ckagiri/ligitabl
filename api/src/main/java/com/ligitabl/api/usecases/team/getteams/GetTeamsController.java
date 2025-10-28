package com.ligitabl.api.usecases.team.getteams;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.ligitabl.api.usecases.team.TeamDto;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/teams")
@RequiredArgsConstructor
public class GetTeamsController {
    private final GetTeamsUseCase getTeamsUseCase;

    @GetMapping
    public List<TeamDto> getTeams() {
        return getTeamsUseCase.execute();
    }
}
