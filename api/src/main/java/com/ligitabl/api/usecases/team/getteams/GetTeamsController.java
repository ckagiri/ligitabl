package com.ligitabl.api.usecases.team.getteams;

import static com.ligitabl.api.shared.UseCase.NO_INPUT;

import java.util.List;

import org.springframework.http.ResponseEntity;
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
    public ResponseEntity<List<TeamDto>> list() {
        var teams = getTeamsUseCase.execute(NO_INPUT);
        return ResponseEntity.ok(TeamDto.listOf(teams));
    }
}
