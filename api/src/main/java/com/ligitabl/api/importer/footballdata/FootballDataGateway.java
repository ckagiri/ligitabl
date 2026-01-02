package com.ligitabl.api.importer.footballdata;

import com.ligitabl.api.importer.model.Entities;
import com.ligitabl.api.importer.model.ImportError;
import com.ligitabl.api.importer.model.ValueObjects;
import com.ligitabl.api.shared.Either;

import java.util.List;

interface FootballDataGateway {

    /**
     * Fetch competition details including current season
     */
    Either<ImportError, Entities.ExternalCompetition> fetchCompetition(ValueObjects.CompetitionCode code);

    /**
     * Fetch all matches for a competition
     */
    Either<ImportError, List<Entities.ExternalMatch>> fetchMatches(ValueObjects.CompetitionCode code);
}
