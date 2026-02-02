package com.ligitabl.api.runners.importer.footballdata;

import java.util.List;

import com.ligitabl.api.runners.importer.model.entities.ExternalCompetition;
import com.ligitabl.api.runners.importer.model.entities.ExternalMatch;
import com.ligitabl.api.runners.importer.model.errors.ImportError;
import com.ligitabl.api.runners.importer.model.valueobjects.CompetitionCode;
import com.ligitabl.api.shared.Either;

public interface FootballDataGateway {

    /**
     * Fetch competition details including current season
     */
    Either<ImportError, ExternalCompetition> fetchCompetition(CompetitionCode code);

    /**
     * Fetch all matches for a competition
     */
    Either<ImportError, List<ExternalMatch>> fetchMatchesForCompetition(CompetitionCode code);
}
