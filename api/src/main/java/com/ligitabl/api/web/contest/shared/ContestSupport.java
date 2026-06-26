package com.ligitabl.api.web.contest.shared;

import java.util.List;

import org.springframework.stereotype.Component;

import com.ligitabl.api.config.CompetitionDefaults;
import com.ligitabl.model.domain.PhaseType;
import com.ligitabl.model.domain.RoundSpan;
import com.ligitabl.model.repo.CompetitionRepo;
import com.ligitabl.model.repo.RoundRepo;
import com.ligitabl.model.repo.SeasonRepo;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class ContestSupport {

    private final CompetitionDefaults competitionDefaults;
    private final CompetitionRepo competitionRepo;
    private final SeasonRepo seasonRepo;
    private final RoundRepo roundRepo;

    public record SprintOption(
            String code, String name, String gwLabel, String status,
            String quarterCode, String quarterName) {}

    public int resolveCurrentRoundPosition() {
        String slug = competitionDefaults.defaultCompetitionSlug();
        var competition = competitionRepo.findBySlug(slug).orElse(null);
        if (competition == null) return 1;

        var season = seasonRepo.findActiveSeason(competition.getId()).orElse(null);
        if (season == null || season.getCurrentRoundId() == null) return 1;

        return roundRepo.findById(season.getCurrentRoundId())
                .map(r -> r.getPosition())
                .orElse(1);
    }

    public List<SprintOption> resolveSprintOptions() {
        String slug = competitionDefaults.defaultCompetitionSlug();
        var competition = competitionRepo.findBySlug(slug).orElse(null);
        if (competition == null || competition.getPhases() == null) return List.of();

        int currentPos = resolveCurrentRoundPosition();
        List<RoundSpan> phases = competition.getPhases();
        List<RoundSpan> quarters = phases.stream()
                .filter(p -> p.getType() == PhaseType.QUARTER)
                .toList();

        return phases.stream()
                .filter(p -> p.getType() == PhaseType.SPRINT)
                .map(sprint -> {
                    String status;
                    if (sprint.getTo() < currentPos) status = "PAST";
                    else if (sprint.getFrom() > currentPos) status = "FUTURE";
                    else status = "OPEN";

                    RoundSpan quarter = quarters.stream()
                            .filter(q -> q.getFrom() <= sprint.getFrom() && q.getTo() >= sprint.getTo())
                            .findFirst()
                            .orElse(null);

                    return new SprintOption(
                            sprint.getCode(), sprint.getName(),
                            "GW " + sprint.getFrom() + "–" + sprint.getTo(),
                            status,
                            quarter != null ? quarter.getCode() : "",
                            quarter != null ? quarter.getName() : "");
                })
                .toList();
    }
}
