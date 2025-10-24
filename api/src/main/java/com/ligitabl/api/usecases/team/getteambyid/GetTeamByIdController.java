package com.ligitabl.api.usecases.team.getteambyid;


import com.ligitabl.model.domain.Team;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/teams")
@RequiredArgsConstructor
public class GetTeamByIdController {

    private final GetTeamByIdUseCase getTeamByIdUseCase;

    @GetMapping("/{id}")
    public ResponseEntity<Team> get(@PathVariable("id") UUID id) {
        Team t = getTeamByIdUseCase.execute(id);
        return t != null ? ResponseEntity.ok(t) : ResponseEntity.notFound().build();
    }
}
