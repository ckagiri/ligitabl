package com.ligitabl.api.usecases.team.getteambyslug;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.ligitabl.api.shared.exceptions.BusinessFailureException;
import com.ligitabl.api.usecases.team.TeamDto;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/teams")
@RequiredArgsConstructor
public class GetTeamBySlugController {
    private final GetTeamBySlugUseCase getTeamBySlugUseCase;

    @GetMapping("/{slug}")
    public ResponseEntity<TeamDto> getBySlug(@PathVariable String slug) {
        var query = new GetTeamBySlugQuery(slug);
        var result = getTeamBySlugUseCase.execute(query);

        return result.fold(
                error -> {
                    throw new BusinessFailureException(error);
                },
                dto -> ResponseEntity.ok(dto));
    }
}
