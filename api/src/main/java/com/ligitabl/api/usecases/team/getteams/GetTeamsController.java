package com.ligitabl.api.usecases.team.getteams;

import com.ligitabl.api.usecases.team.TeamResponseDto;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

import static com.ligitabl.api.shared.UseCase.NO_INPUT;

@RestController
@RequestMapping("/api/teams")
@RequiredArgsConstructor
public class GetTeamsController {

    private final GetTeamsUseCase getTeamsUseCase;

    @GetMapping
    public ResponseEntity<List<TeamResponseDto>> list() {
        var teams = getTeamsUseCase.execute(NO_INPUT);
        return ResponseEntity.ok(TeamResponseDto.listOf(teams));
    }
}
