package com.ligitabl.api.rest.standings;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Component;

import com.ligitabl.model.domain.Match;
import com.ligitabl.model.repo.MatchRepo;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class FormService {

    private final MatchRepo matchRepo;

    public Map<String, List<FormEntry>> buildFormMap(UUID seasonId, int roundPosition) {
        List<Match> finished = matchRepo.findFinishedMatchesUpToRoundWithTeams(seasonId, roundPosition);
        if (finished == null || finished.isEmpty()) {
            return Map.of();
        }

        List<Match> sorted = finished.stream()
                .filter(m -> m.hasTeamsLoaded() && m.isPlayed())
                .sorted(Comparator.comparing(Match::getKickOff, Comparator.nullsFirst(Comparator.naturalOrder())))
                .toList();

        Map<String, List<FormEntry>> accumulator = new HashMap<>();
        for (Match match : sorted) {
            String homeCode = match.getHomeTeam().getCode();
            String awayCode = match.getAwayTeam().getCode();
            int homeGoals = match.result().map(r -> r.homeGoals()).orElse(0);
            int awayGoals = match.result().map(r -> r.awayGoals()).orElse(0);

            String homeResult = homeGoals > awayGoals ? "W" : (homeGoals == awayGoals ? "D" : "L");
            String awayResult = awayGoals > homeGoals ? "W" : (homeGoals == awayGoals ? "D" : "L");

            accumulator
                    .computeIfAbsent(homeCode, k -> new ArrayList<>())
                    .add(new FormEntry(homeResult, true, awayCode, homeGoals, awayGoals));
            accumulator
                    .computeIfAbsent(awayCode, k -> new ArrayList<>())
                    .add(new FormEntry(awayResult, false, homeCode, awayGoals, homeGoals));
        }

        accumulator.replaceAll((code, entries) -> {
            int size = entries.size();
            return size <= 5 ? entries : new ArrayList<>(entries.subList(size - 5, size));
        });

        return accumulator;
    }
}
