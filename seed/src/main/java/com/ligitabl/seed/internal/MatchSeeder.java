package com.ligitabl.seed.internal;

import static com.ligitabl.model.db.tables.TMatch.T_MATCH;
import static com.ligitabl.model.db.tables.TRound.T_ROUND;
import static com.ligitabl.model.db.tables.TSeason.T_SEASON;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ligitabl.model.domain.Score;
import com.ligitabl.seed.internal.ReferenceResolver.TeamInfo;
import com.ligitabl.seed.internal.config.MatchSeedConfig;
import com.ligitabl.seed.internal.schedule.Match;
import com.ligitabl.seed.internal.schedule.Round;
import com.ligitabl.seed.internal.schedule.ScheduleGenerator;
import com.ligitabl.seed.internal.schedule.KickoffGenerator;
import com.ligitabl.seed.internal.scoring.MatchScore;
import com.ligitabl.seed.internal.scoring.StrengthAwareScoreGenerator;
import com.ligitabl.seed.internal.scoring.TeamProfile;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Comparator;
import java.util.HashMap;
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
    private StrengthAwareScoreGenerator scoreGenerator;
    private KickoffGenerator kickoffGenerator;

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
        // Initialize generators
        long seed = config.getRandomSeed() != null ? config.getRandomSeed() : System.currentTimeMillis();
        this.scoreGenerator = new StrengthAwareScoreGenerator(seed);
        this.kickoffGenerator = new KickoffGenerator(seed);

        UUID seasonId = referenceResolver.resolveSeasonId(
                config.getCompetitionSlug(),
                config.getSeasonSlug());

        // Load season data including start date and team ratings
        SeasonData seasonData = loadSeasonData(seasonId, config.getSeasonSlug());
        Map<Integer, UUID> roundIdsByPosition = loadRoundIds(seasonId);
        List<Round<TeamInfo>> schedule = scheduleGenerator.generateSchedule(seasonData.teams);

        seedMatches(schedule, roundIdsByPosition, seasonData, config);
    }

    @Override
    protected String getSeederName() {
        return "match";
    }

    private SeasonData loadSeasonData(UUID seasonId, String seasonSlug) {
        var seasonRecord = dsl.select(T_SEASON.C_INITIAL_RANKINGS, T_SEASON.C_START_DATE)
                .from(T_SEASON)
                .where(T_SEASON.PK_ID.eq(seasonId))
                .fetchOne();

        if (seasonRecord == null) {
            throw new IllegalStateException("Season not found: " + seasonSlug);
        }

        JSONB teamsJson = seasonRecord.get(T_SEASON.C_INITIAL_RANKINGS);
        LocalDate startDate = seasonRecord.get(T_SEASON.C_START_DATE);

        if (teamsJson == null) {
            throw new IllegalStateException(
                    "Season '" + seasonSlug + "' has no teams configured");
        }

        List<Map<String, Object>> teamsData = deserializeTeams(teamsJson, seasonSlug);
        validateTeamCount(teamsData, seasonSlug);

        // Extract team codes and ratings
        List<TeamData> teamDataList = extractTeamData(teamsData, seasonSlug);

        // Resolve team info from database
        List<String> teamCodes = teamDataList.stream()
                .map(TeamData::code)
                .collect(Collectors.toList());
        Map<String, TeamInfo> teamsByCode = referenceResolver.resolveTeams(teamCodes);

        // Build team profiles with ratings
        List<TeamInfo> teams = teamDataList.stream()
                .map(td -> teamsByCode.get(td.code()))
                .collect(Collectors.toList());

        // Create rating map for score generation
        Map<String, Integer> ratingsByCode = teamDataList.stream()
                .collect(Collectors.toMap(TeamData::code, TeamData::rating));

        return new SeasonData(teams, ratingsByCode, startDate);
    }

    private List<Map<String, Object>> deserializeTeams(JSONB teamsJson, String seasonSlug) {
        try {
            List<Map<String, Object>> teams = objectMapper.readValue(
                    teamsJson.data(),
                    new TypeReference<List<Map<String, Object>>>() {});

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

    private List<TeamData> extractTeamData(List<Map<String, Object>> teams, String seasonSlug) {
        return teams.stream()
                .map(team -> {
                    Object codeObj = team.get("code");
                    Object ratingObj = team.get("rating");

                    if (codeObj == null || codeObj.toString().trim().isEmpty()) {
                        throw new IllegalStateException(
                                "Season '" + seasonSlug + "' has team entry without valid code: " + team);
                    }

                    String code = codeObj.toString().trim();
                    int rating = ratingObj != null ? ((Number) ratingObj).intValue() : 50;

                    return new TeamData(code, rating);
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
            SeasonData seasonData,
            MatchSeedConfig config) {

        for (Round<TeamInfo> round : schedule) {
            UUID roundId = roundIdsByPosition.get(round.position());
            if (roundId == null) {
                throw new IllegalStateException(
                        "No round found for position " + round.position()
                                + " in season '" + config.getSeasonSlug() + "'");
            }

            // Generate kickoff times for this round
            List<LocalDateTime> kickoffs = kickoffGenerator.generateKickoffs(
                    round.position(),
                    round.matches().size(),
                    seasonData.startDate);

            // Seed each match with its kickoff time
            for (int i = 0; i < round.matches().size(); i++) {
                Match<TeamInfo> match = round.matches().get(i);
                LocalDateTime kickoff = kickoffs.get(i);
                seedSingleMatch(match, roundId, round.position(), kickoff, seasonData, config);
            }
        }
    }

    private void seedSingleMatch(
            Match<TeamInfo> match,
            UUID roundId,
            int roundPosition,
            LocalDateTime kickoff,
            SeasonData seasonData,
            MatchSeedConfig config) {

        TeamInfo home = match.home();
        TeamInfo away = match.away();

        String slug = generateSlug(home.tla(), away.tla());
        String status = config.getStatusForRound(roundPosition);
        JSONB score = null;

        // Generate score for finished rounds using strength-aware generator
        if (config.shouldHaveScore(roundPosition)) {
            TeamProfile homeProfile = new TeamProfile(
                    home.id(),
                    home.name(),
                    home.tla(),
                    seasonData.ratingsByCode.getOrDefault(home.tla(), 50));

            TeamProfile awayProfile = new TeamProfile(
                    away.id(),
                    away.name(),
                    away.tla(),
                    seasonData.ratingsByCode.getOrDefault(away.tla(), 50));

            MatchScore generatedScore = scoreGenerator.generateScore(homeProfile, awayProfile);
            Score modelScore = Score.builder()
                    .homeGoals(generatedScore.getHomeGoals())
                    .awayGoals(generatedScore.getAwayGoals())
                    .build();
            score = serializeScore(modelScore);
        }

        OffsetDateTime kickOff = kickoff == null ? null : kickoff.atOffset(ZoneOffset.UTC);

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
                        status,
                        roundPosition,
                        score,
                    kickOff,
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

    private record TeamData(String code, int rating) {}

    private record SeasonData(
            List<TeamInfo> teams,
            Map<String, Integer> ratingsByCode,
            LocalDate startDate) {}

    private JSONB serializeScore(Score score) {
        if (score == null) {
            return null;
        }
        try {
            return JSONB.valueOf(objectMapper.writeValueAsString(score));
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Failed to serialize match score JSON", e);
        }
    }
}
