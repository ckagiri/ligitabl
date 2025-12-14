package com.ligitabl.api.importer;

import com.ligitabl.model.domain.Season;
import com.ligitabl.model.repo.SeasonRepo;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;

/**
 * Service responsible for importing matches from the external API.
 *
 * For now this focuses on fetching and counting matches. Persistence into
 * Ligitabl's own tables will be added next by wiring in the model repos
 * (TeamRepo, RoundRepo, MatchRepo) and using Team.clientId / Match.clientId
 * as the mapping keys.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MatchImportService {

    private final FootballDataClient footballDataClient;
    private final SeasonRepo seasonRepo;

    @Transactional
    public ImportResult importMatchesForCompetition(String competitionCode) {
        ImportResult result = new ImportResult();
        result.setCompetitionCode(competitionCode);

        try {
            // Resolve season via the competition endpoint so we can
            // reliably map external currentSeason.id -> Season.clientId
            ExternalCompetitionDto competition = footballDataClient.fetchCompetition(competitionCode);
            if (competition == null || competition.getCurrentSeason() == null) {
                throw new IllegalStateException("No current season found for competition " + competitionCode);
            }

            Integer externalSeasonId = competition.getCurrentSeason().getId();
            if (externalSeasonId == null) {
                throw new IllegalStateException("External season id is null for competition " + competitionCode);
            }

            Season season = seasonRepo.findByClientId(externalSeasonId)
                    .orElseThrow(() -> new IllegalStateException(
                            "Season with clientId " + externalSeasonId + " not found in Ligitabl DB"));

            result.setSeasonName(season.getName());
            log.info("Resolved season: {} (clientId: {})", season.getName(), externalSeasonId);

            ExternalMatchDto.MatchesResponse response =
                    footballDataClient.fetchMatchesForCompetition(competitionCode);

            if (response == null || response.getMatches() == null) {
                log.warn("No matches returned from API for {}", competitionCode);
                result.setSuccess(true);
                result.setMessage("No matches found");
                return result;
            }

            int total = response.getMatches().size();
            log.info("Received {} matches from external API for {}", total, competitionCode);
            // For now, treat `created` as the number of matches fetched
            // from the external API. Once persistence is added, this will
            // reflect the number of rows inserted.
            result.setCreated(total);
            result.setUpdated(0);
            result.setFailed(0);
            result.setSuccess(true);
            result.setMessage("Fetched " + total + " matches (persistence not yet implemented)");

        } catch (Exception e) {
            log.error("Error importing matches for {}", competitionCode, e);
            result.setSuccess(false);
            result.setFailed(1);
            result.setErrors(Collections.singletonList(e.getMessage()));
            result.setMessage("Import failed: " + e.getMessage());
        }

        return result;
    }

    // Season name is always resolved via SeasonRepo.findByClientId; if that fails,
    // importMatchesForCompetition will throw, since the mapping must be present.

    @Data
    public static class ImportResult {
        private boolean success;
        private String message;
        private String competitionCode;
        private String seasonName;
        private int created;
        private int updated;
        private int failed;
        private java.util.List<String> errors;
    }
}
