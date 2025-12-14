package com.ligitabl.api.importer;

import java.util.Collections;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ligitabl.model.domain.Match;
import com.ligitabl.model.domain.MatchStatus;
import com.ligitabl.model.domain.Season;
import com.ligitabl.model.domain.Team;
import com.ligitabl.model.repo.MatchRepo;
import com.ligitabl.model.repo.RoundRepo;
import com.ligitabl.model.repo.SeasonRepo;
import com.ligitabl.model.repo.TeamRepo;

import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Service responsible for importing matches from the external API and
 * persisting them into Ligitabl's own tables using Team.clientId and
 * Match.clientId as mapping keys.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MatchImportService {

    private final FootballDataClient footballDataClient;
    private final SeasonRepo seasonRepo;
    private final TeamRepo teamRepo;
    private final RoundRepo roundRepo;
    private final MatchRepo matchRepo;

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

            Season season = seasonRepo
                    .findByClientId(externalSeasonId)
                    .orElseThrow(() -> new IllegalStateException(
                            "Season with clientId " + externalSeasonId + " not found in Ligitabl DB"));

            result.setSeasonName(season.getName());
            log.info("Resolved season: {} (clientId: {})", season.getName(), externalSeasonId);

            ExternalMatchDto.MatchesResponse response = footballDataClient.fetchMatchesForCompetition(competitionCode);

            if (response == null
                    || response.getMatches() == null
                    || response.getMatches().isEmpty()) {
                log.warn("No matches returned from API for {}", competitionCode);
                result.setSuccess(true);
                result.setMessage("No matches found");
                return result;
            }

            int created = 0;
            int updated = 0;

            for (ExternalMatchDto externalMatch : response.getMatches()) {
                if (externalMatch == null || externalMatch.getId() == null) {
                    throw new IllegalStateException(
                            "External match without id encountered for competition " + competitionCode);
                }

                Match domainMatch = mapToDomainMatch(externalMatch, season);

                var existing = matchRepo.findByClientId(domainMatch.getClientId());
                if (existing.isPresent()) {
                    domainMatch.setId(existing.get().getId());
                    matchRepo.update(domainMatch);
                    updated++;
                } else {
                    matchRepo.create(domainMatch);
                    created++;
                }
            }

            result.setCreated(created);
            result.setUpdated(updated);
            result.setFailed(0);
            result.setSuccess(true);
            result.setMessage("Imported " + created + " new matches and updated " + updated + " for competition "
                    + competitionCode);

        } catch (Exception e) {
            log.error("Error importing matches for {}", competitionCode, e);
            result.setSuccess(false);
            result.setFailed(1);
            result.setErrors(Collections.singletonList(e.getMessage()));
            result.setMessage("Import failed: " + e.getMessage());
        }

        return result;
    }

    private Match mapToDomainMatch(ExternalMatchDto externalMatch, Season season) {
        // Resolve round by season + matchday (position)
        int matchday = externalMatch.getMatchday() == null ? 0 : externalMatch.getMatchday();
        var round = roundRepo
                .findBySeasonIdAndPosition(season.getId(), matchday)
                .orElseThrow(() -> new IllegalStateException(
                        "Round not found for season " + season.getSlug() + " and position " + matchday));

        // Resolve teams by external clientId
        ExternalMatchDto.TeamDTO homeTeamDto = externalMatch.getHomeTeam();
        ExternalMatchDto.TeamDTO awayTeamDto = externalMatch.getAwayTeam();

        if (homeTeamDto == null || homeTeamDto.getId() == null) {
            throw new IllegalStateException("Home team missing clientId for match " + externalMatch.getId());
        }
        if (awayTeamDto == null || awayTeamDto.getId() == null) {
            throw new IllegalStateException("Away team missing clientId for match " + externalMatch.getId());
        }

        Team homeTeam = teamRepo.findByClientId(homeTeamDto.getId())
                .orElseThrow(() -> new IllegalStateException("Home team with clientId " + homeTeamDto.getId()
                        + " not found for match " + externalMatch.getId()));

        Team awayTeam = teamRepo.findByClientId(awayTeamDto.getId())
                .orElseThrow(() -> new IllegalStateException("Away team with clientId " + awayTeamDto.getId()
                        + " not found for match " + externalMatch.getId()));

        String slug = buildMatchSlug(season, matchday, homeTeam, awayTeam);

        MatchStatus status = mapStatus(externalMatch.getStatus());

        return Match.builder()
                .clientId(externalMatch.getId())
                .roundId(round.getId())
                .homeTeamId(homeTeam.getId())
                .awayTeamId(awayTeam.getId())
                .slug(slug)
                .status(status)
                .kickOff(externalMatch.getUtcDate())
                .venue(null)
                .matchday(matchday)
                .score(null)
                .build();
    }

    private String buildMatchSlug(Season season, int matchday, Team homeTeam, Team awayTeam) {
        String seasonSlug = season.getSlug() == null ? "" : season.getSlug().value();
        String home = homeTeam.getTla() == null ? "" : homeTeam.getTla().toLowerCase();
        String away = awayTeam.getTla() == null ? "" : awayTeam.getTla().toLowerCase();
        return seasonSlug + "-md" + matchday + "-" + home + "-" + away;
    }

    private MatchStatus mapStatus(String externalStatus) {
        if (externalStatus == null) {
            return MatchStatus.SCHEDULED;
        }
        String normalized = externalStatus.toUpperCase();
        try {
            return MatchStatus.valueOf(normalized);
        } catch (IllegalArgumentException ex) {
            // Fallback for statuses we don't model yet
            return MatchStatus.SCHEDULED;
        }
    }

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
