package com.ligitabl.api.usecases.team.getteambyid;

import com.ligitabl.api.shared.exceptions.BusinessFailureException;
import com.ligitabl.api.usecases.team.TeamDto;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/teams")
@RequiredArgsConstructor
public class GetTeamByIdController {

    private final GetTeamByIdUseCase getTeamByIdUseCase;

    @GetMapping(params = "id")
    public ResponseEntity<TeamDto> getById(@RequestParam("id") String id) {
        var query = new GetTeamByIdQuery(id);
        var result = getTeamByIdUseCase.execute(query);

        return result.fold(
            error -> {
                throw new BusinessFailureException(error);
            },
            dto -> ResponseEntity.ok(dto));
    }
}
