package com.ligitabl.seed.internal;

import static com.ligitabl.model.db.tables.TMatch.T_MATCH;
import static com.ligitabl.model.db.tables.TRound.T_ROUND;
import static com.ligitabl.model.db.tables.TSeason.T_SEASON;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ligitabl.seed.internal.ReferenceResolver.TeamInfo;
import com.ligitabl.seed.internal.config.MatchSeedConfig;
import com.ligitabl.seed.internal.schedule.Match;
import com.ligitabl.seed.internal.schedule.Round;
import com.ligitabl.seed.internal.schedule.ScheduleGenerator;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.jooq.DSLContext;
import org.jooq.JSONB;

public class MatchSeeder extends AbstractSeeder<MatchSeedConfig> {

        private final ObjectMapper objectMapper;
        private final ReferenceResolver referenceResolver;
        private final ScheduleGenerator<TeamInfo> scheduleGenerator;

        public MatchSeeder(DSLContext dsl, ObjectMapper objectMapper) {
                super(dsl);
                this.objectMapper = objectMapper;
                this.referenceResolver = new ReferenceResolver(dsl);
                this.scheduleGenerator = new ScheduleGenerator<>();
        }

        @Override
        protected boolean isValidConfig(MatchSeedConfig config) {
                return config != null;
        }

        @Override
        protected void performSeed(MatchSeedConfig config) {
                UUID seasonId = referenceResolver.resolveSeasonId(
                                config.getCompetitionSlug(), config.getSeasonSlug());

                List<TeamInfo> teams = loadTeamsFromSeason(seasonId, config.getSeasonSlug());
                Map<Integer, UUID> roundIdsByPosition = loadRoundIds(seasonId);
                List<Round<TeamInfo>> schedule = scheduleGenerator.generateSchedule(teams);

                seedMatches(schedule, roundIdsByPosition, config);
        }

        @Override
        protected String getSeederName() {
                return "match";
        }

        private List<TeamInfo> loadTeamsFromSeason(UUID seasonId, String seasonSlug) {
                JSONB teamsJson = dsl.select(T_SEASON.C_TEAMS)
                                .from(T_SEASON)
                                .where(T_SEASON.PK_ID.eq(seasonId))
                                .fetchOne(T_SEASON.C_TEAMS);

                if (teamsJson == null) {
                        throw new IllegalStateException(
                                        "Season '" + seasonSlug + "' has no teams configured in C_TEAMS column");
                }

                List<Map<String, Object>> teamsData = deserializeTeams(teamsJson, seasonSlug);
                validateTeamCount(teamsData, seasonSlug);

                List<String> teamCodes = extractTeamCodes(teamsData, seasonSlug);
                Map<String, TeamInfo> teamsByCode = referenceResolver.resolveTeams(teamCodes);

                return teamCodes.stream().map(teamsByCode::get).collect(Collectors.toList());
        }

        private List<Map<String, Object>> deserializeTeams(JSONB teamsJson, String seasonSlug) {
                try {
                        List<Map<String, Object>> teams = objectMapper.readValue(
                                        teamsJson.data(), new TypeReference<List<Map<String, Object>>>() {});

                        return teams.stream()
                                        .sorted(Comparator.comparing(t -> (Integer) t.getOrDefault("position", 0)))
                                        .toList();
                } catch (Exception e) {
                        throw new IllegalArgumentException(
                                        "Failed to parse teams JSON for season '" + seasonSlug + "'", e);
                }
        }

        private void validateTeamCount(List<Map<String, Object>> teams, String seasonSlug) {
                if (teams == null || teams.size() < 2 || teams.size() % 2 != 0) {
                        throw new IllegalStateException(
                                        "Season '" + seasonSlug + "' must have an even number of teams (>= 2). Found: "
                                                        + (teams == null ? 0 : teams.size()));
                }
        }

        private List<String> extractTeamCodes(List<Map<String, Object>> teams, String seasonSlug) {
                return teams.stream()
                                .map(team -> {
                                        Object code = team.get("code");
                                        if (code == null || code.toString().trim().isEmpty()) {
                                                throw new IllegalStateException(
                                                                "Season '" + seasonSlug + "' has team entry without valid code: " + team);
                                        }
                                        return code.toString().trim();
                                })
                                .toList();
        }

        private Map<Integer, UUID> loadRoundIds(UUID seasonId) {
                return dsl.select(T_ROUND.C_POSITION, T_ROUND.PK_ID)
                                .from(T_ROUND)
                                .where(T_ROUND.FK_SEASON_ID.eq(seasonId))
                                .fetch()
                                .intoMap(T_ROUND.C_POSITION, T_ROUND.PK_ID);
        }

        private void seedMatches(
                        List<Round<TeamInfo>> schedule,
                        Map<Integer, UUID> roundIdsByPosition,
                        MatchSeedConfig config) {

                for (Round<TeamInfo> round : schedule) {
                        UUID roundId = roundIdsByPosition.get(round.position());
                        if (roundId == null) {
                                throw new IllegalStateException(
                                                "No round found for position " + round.position()
                                                                + " in season '" + config.getSeasonSlug() + "'");
                        }

                        for (Match<TeamInfo> match : round.matches()) {
                                seedSingleMatch(match, roundId, round.position(), config);
                        }
                }
        }

        private void seedSingleMatch(
                        Match<TeamInfo> match,
                        UUID roundId,
                        int roundPosition,
                        MatchSeedConfig config) {

                TeamInfo home = match.home();
                TeamInfo away = match.away();

                String slug = generateSlug(home.tla(), away.tla());

                int rowsAffected = dsl.insertInto(
                                                T_MATCH,
                                                T_MATCH.PK_ID,
                                                T_MATCH.C_CLIENT_ID,
                                                T_MATCH.FK_ROUND_ID,
                                                T_MATCH.FK_HOME_TEAM_ID,
                                                T_MATCH.FK_AWAY_TEAM_ID,
                                                T_MATCH.C_SLUG,
                                                T_MATCH.C_STATUS,
                                                T_MATCH.C_MATCHDAY,
                                                T_MATCH.C_SCORE,
                                                T_MATCH.C_KICK_OFF,
                                                T_MATCH.C_VENUE)
                                .values(
                                                UUID.randomUUID(),
                                                config.getClientId(),
                                                roundId,
                                                home.id(),
                                                away.id(),
                                                slug,
                                                config.getStatus(),
                                                roundPosition,
                                                null,
                                                null,
                                                null)
                                .onConflict(T_MATCH.C_SLUG)
                                .doNothing()
                                .execute();

                if (rowsAffected > 0) {
                        recordInsert();
                } else {
                        recordSkip();
                }
        }

        private String generateSlug(String homeTla, String awayTla) {
                String home = homeTla == null ? "" : homeTla.toLowerCase();
                String away = awayTla == null ? "" : awayTla.toLowerCase();
                return home + "-" + away;
        }
}

