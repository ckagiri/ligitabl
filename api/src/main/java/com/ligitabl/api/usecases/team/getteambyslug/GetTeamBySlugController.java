package com.ligitabl.api.usecases.team.getteambyslug;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.ligitabl.api.shared.exceptions.UseCaseException;
import com.ligitabl.api.usecases.team.TeamDto;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@RestController
@RequestMapping("/api/teams")
@RequiredArgsConstructor
@Slf4j
public class GetTeamBySlugController {
    private final GetTeamBySlugPort getTeamBySlugUseCase;

    @GetMapping("/{slug}")
    public ResponseEntity<TeamDto> getBySlug(@PathVariable String slug) {
        log.info("GetTeamBySlug request slug={}", slug);
        var query = new GetTeamBySlugQuery(slug);
        var result = getTeamBySlugUseCase.execute(query);

        return result.fold(
                error -> {
                    throw new UseCaseException(error);
                },
                dto -> {
                    log.debug("GetTeamBySlug success slug={} id={}", slug, dto.getId());
                    return ResponseEntity.ok(dto);
                });
    }
}
