package com.ligitabl.api.usecases.team.getteambyid;

import com.ligitabl.api.shared.exceptions.BusinessFailureException;
import com.ligitabl.api.usecases.team.TeamResponseDto;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/teams")
@RequiredArgsConstructor
public class GetTeamByIdController {

    private final GetTeamByIdUseCase getTeamByIdUseCase;

    @GetMapping("/{id}")
    public ResponseEntity<TeamResponseDto> get(@PathVariable("id") String id) {
        var query = new GetTeamByIdQuery(id);
        var result = getTeamByIdUseCase.execute(query);

        return result.fold(
                error -> {
                    throw new BusinessFailureException(error);
                },
                team -> ResponseEntity.ok(TeamResponseDto.from(result.getValue())));
    }
}
