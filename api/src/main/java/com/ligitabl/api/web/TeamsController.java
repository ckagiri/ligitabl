package com.ligitabl.api.web;


import com.ligitabl.model.domain.Team;
import com.ligitabl.model.repo.TeamRepo;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/teams")
@RequiredArgsConstructor
public class TeamsController {

    private final TeamRepo teamRepo;

    @GetMapping
    public ResponseEntity<List<Team>> list() {
        return ResponseEntity.ok(teamRepo.findAll());
    }

    @GetMapping("/{id}")
    public ResponseEntity<Team> get(@PathVariable("id") UUID id) {
        Team t = teamRepo.findById(id);
        return t != null ? ResponseEntity.ok(t) : ResponseEntity.notFound().build();
    }
}
