package com.ligitabl.api.importer.footballdata;

import com.ligitabl.api.importer.model.entities.ExternalCompetition;
import com.ligitabl.api.importer.model.entities.ExternalMatch;
import com.ligitabl.api.importer.model.errors.ImportError;
import com.ligitabl.api.importer.model.valueobjects.CompetitionCode;
import com.ligitabl.api.shared.Either;

import java.util.List;

public interface FootballDataGateway {

    /**
     * Fetch competition details including current season
     */
    Either<ImportError, ExternalCompetition> fetchCompetition(CompetitionCode code);

    /**
     * Fetch all matches for a competition
     */
    Either<ImportError, List<ExternalMatch>> fetchMatches(CompetitionCode code);
}
