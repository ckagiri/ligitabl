package com.ligitabl.seed.internal;

import static com.ligitabl.model.db.tables.TCompetition.T_COMPETITION;
import static com.ligitabl.model.db.tables.TSeason.T_SEASON;
import static com.ligitabl.model.db.tables.TTeam.T_TEAM;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.jooq.DSLContext;
import org.jooq.Record3;

/**
 * Service responsible for resolving foreign key references.
 * Centralizes all lookup logic to avoid duplication across seeders.
 */
public class ReferenceResolver {

    private final DSLContext dsl;

    public ReferenceResolver(DSLContext dsl) {
        this.dsl = dsl;
    }

    /**
     * Resolves a competition ID by its slug.
     */
    public UUID resolveCompetitionId(String competitionSlug) {
        UUID id = dsl.select(T_COMPETITION.PK_ID)
                .from(T_COMPETITION)
                .where(T_COMPETITION.C_SLUG.eq(competitionSlug))
                .fetchOne(T_COMPETITION.PK_ID);

        if (id == null) {
            throw new IllegalStateException(
                    "Competition not found with slug: '" + competitionSlug + "'");
        }

        return id;
    }

    /**
     * Resolves a season ID by competition and season slugs.
     */
    public UUID resolveSeasonId(String competitionSlug, String seasonSlug) {
        UUID competitionId = resolveCompetitionId(competitionSlug);

        UUID id = dsl.select(T_SEASON.PK_ID)
                .from(T_SEASON)
                .where(T_SEASON.FK_COMPETITION_ID.eq(competitionId)
                        .and(T_SEASON.C_SLUG.eq(seasonSlug)))
                .fetchOne(T_SEASON.PK_ID);

        if (id == null) {
            throw new IllegalStateException(
                    "Season not found with competitionSlug: '" + competitionSlug
                            + "', seasonSlug: '" + seasonSlug + "'");
        }

        return id;
    }

    /**
     * Resolves team information by TLA codes.
     */
    public Map<String, TeamInfo> resolveTeams(List<String> tlaCodes) {
        List<Record3<UUID, String, String>> teamRows = dsl
                .select(T_TEAM.PK_ID, T_TEAM.C_NAME, T_TEAM.C_TLA)
                .from(T_TEAM)
                .where(T_TEAM.C_TLA.in(tlaCodes))
                .fetch();

        Map<String, TeamInfo> teamsByCode = teamRows.stream()
                .filter(row -> row.get(T_TEAM.C_TLA) != null)
                .collect(Collectors.toMap(
                        row -> row.get(T_TEAM.C_TLA).trim(),
                        row -> new TeamInfo(
                                row.get(T_TEAM.PK_ID),
                                row.get(T_TEAM.C_NAME),
                                row.get(T_TEAM.C_TLA).trim())));

        List<String> missing = tlaCodes.stream()
                .filter(code -> !teamsByCode.containsKey(code))
                .toList();

        if (!missing.isEmpty()) {
            throw new IllegalStateException(
                    "Teams not found with TLA codes: " + missing
                            + ". Please seed teams first.");
        }

        return teamsByCode;
    }

    public record TeamInfo(UUID id, String name, String tla) {}
}
