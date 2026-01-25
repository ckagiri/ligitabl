package com.ligitabl.api.presentation.mapper;

import com.ligitabl.api.presentation.domain.prediction.RankingSource;
import com.ligitabl.api.presentation.dto.response.RankingDTO;
import com.ligitabl.api.presentation.dto.response.SeasonPredictionResponse;
import com.ligitabl.api.presentation.usecase.GetUserPredictionUseCase;
import com.ligitabl.model.domain.SeasonPrediction;
import com.ligitabl.model.domain.TeamRank;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Maps between domain models and presentation DTOs for season predictions.
 *
 * <p>This mapper is part of the presentation layer and handles conversions
 * between application/domain objects and HTTP request/response DTOs.</p>
 */
@Component
public class SeasonPredictionViewMapper {

    /**
     * Map GetSeasonPredictionUseCase result to response DTO.
     *
     * @param result the use case result with rankings and source
     * @return season prediction response DTO
     */
    public SeasonPredictionResponse toResponse(GetUserPredictionUseCase.RankingsWithSource result) {
        List<RankingDTO> rankingDTOs = result.rankings().stream()
                .map(this::toRankingDTO)
                .collect(Collectors.toList());

        return new SeasonPredictionResponse(
                result.source().name(),
                rankingDTOs,
                null // No atRound for fallback sources
        );
    }

    /**
     * Map SeasonPrediction domain model to response DTO.
     *
     * @param prediction the season prediction
     * @return season prediction response DTO
     */
    public SeasonPredictionResponse toResponse(SeasonPrediction prediction) {
        List<RankingDTO> rankingDTOs = prediction.getCurrentRankings().stream()
                .map(this::toRankingDTO)
                .collect(Collectors.toList());

        return new SeasonPredictionResponse(
                RankingSource.USER_PREDICTION.name(),
                rankingDTOs,
                String.valueOf(prediction.getAtRound().value())
        );
    }

    /**
     * Map CreateSeasonPredictionUseCase result to response DTO.
     *
     * @param result the created result with prediction and contest entry
     * @return season prediction response DTO
     */
    public SeasonPredictionResponse toResponse(CreateSeasonPredictionUseCase.CreatedResult result) {
        return toResponse(result.prediction());
    }

    /**
     * Map CreateSeasonPredictionRequest to list of TeamRanking domain objects.
     *
     * @param request the create request
     * @return list of team rankings
     */
    public List<TeamRanking> toTeamRankings(CreateSeasonPredictionRequest request) {
        return request.getTeamRankings().stream()
                .map(dto -> TeamRanking.create(
                        TeamId.of(dto.getTeamId()),
                        dto.getPosition()
                ))
                .collect(Collectors.toList());
    }

    /**
     * Map TeamRanking domain object to RankingDTO.
     *
     * @param ranking the team ranking
     * @return ranking DTO
     */
    public RankingDTO toRankingDTO(TeamRank ranking) {
        // For now, create basic team DTO from TeamId
        // In a real implementation, we'd look up full team details from a repository
        RankingDTO.TeamDTO teamDTO = createTeamDTO(ranking.getCode());

        return new RankingDTO(ranking.getPosition(), teamDTO);
    }

    /**
     * Create a basic TeamDTO from TeamId.
     *
     * <p>TODO: Look up full team details from a repository/service.
     * For now, we extract the code from the team ID format.</p>
     *
     * @param teamId the team ID
     * @return team DTO
     */
    private RankingDTO.TeamDTO createTeamDTO(TeamId teamId) {
        String id = teamId.value();

        // Extract team code from ID (format: "team-{code}-{uuid}")
        String code = extractTeamCode(id);
        String name = getTeamName(code);

        return new RankingDTO.TeamDTO(
                id,
                code,
                name,
                null  // Crest URLs not used in this view
        );
    }

    /**
     * Extract team code from team ID.
     *
     * @param teamId the team ID string
     * @return team code (e.g., "MCI", "ARS")
     */
    private String extractTeamCode(String teamId) {
        // Format: "team-mci-000000000001"
        if (teamId.startsWith("team-")) {
            String[] parts = teamId.split("-");
            if (parts.length >= 2) {
                return parts[1].toUpperCase();
            }
        }
        return "UNK"; // Unknown team
    }

    /**
     * Get team name from team code.
     *
     * <p>TODO: Look up from repository.</p>
     *
     * @param code the team code
     * @return team name
     */
    private String getTeamName(String code) {
        return switch (code) {
            case "MCI" -> "Manchester City";
            case "ARS" -> "Arsenal";
            case "LIV" -> "Liverpool";
            case "AVL" -> "Aston Villa";
            case "TOT" -> "Tottenham";
            case "CHE" -> "Chelsea";
            case "NEW" -> "Newcastle";
            case "MUN" -> "Man United";
            case "WHU" -> "West Ham";
            case "BHA" -> "Brighton";
            case "WOL" -> "Wolves";
            case "FUL" -> "Fulham";
            case "BOU" -> "Bournemouth";
            case "CRY" -> "Crystal Palace";
            case "BRE" -> "Brentford";
            case "EVE" -> "Everton";
            case "NFO" -> "Nottingham Forest";
            case "LEE" -> "Leeds United";
            case "BUR" -> "Burnley";
            case "SUN" -> "Sunderland";
            default -> "Unknown Team";
        };
    }
}
