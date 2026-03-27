package com.ligitabl.model.infra;

import static com.ligitabl.model.db.tables.TMatch.T_MATCH;
import static com.ligitabl.model.db.tables.TRound.T_ROUND;
import static com.ligitabl.model.db.tables.TTeam.T_TEAM;

import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.jooq.DSLContext;
import org.jooq.JSONB;
import org.jooq.RecordMapper;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ligitabl.model.db.tables.records.MatchRecord;
import com.ligitabl.model.db.tables.records.TeamRecord;
import com.ligitabl.model.domain.Match;
import com.ligitabl.model.domain.MatchStatus;
import com.ligitabl.model.domain.Score;
import com.ligitabl.model.domain.Team;
import com.ligitabl.model.domain.TeamSlug;
import com.ligitabl.model.repo.MatchRepo;

import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class MatchPersistenceAdapter implements MatchRepo {
    private final DSLContext dsl;

    private static final MatchRecordMapper MAPPER = new MatchRecordMapper();
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Override
    public List<Match> findByRoundId(UUID roundId) {
        return dsl.selectFrom(T_MATCH)
                .where(T_MATCH.FK_ROUND_ID.eq(roundId))
                .orderBy(T_MATCH.C_KICK_OFF.asc())
                .fetch()
                .map(MAPPER::map);
    }

    @Override
    public Optional<Match> findByClientId(Integer clientId) {
        var record =
                dsl.selectFrom(T_MATCH).where(T_MATCH.C_CLIENT_ID.eq(clientId)).fetchOne();

        return Optional.ofNullable(MAPPER.map(record));
    }

    @Override
    public Optional<Match> findByRoundIdAndSlug(UUID roundId, String slug) {
        if (roundId == null) {
            throw new IllegalArgumentException("roundId must not be null");
        }
        if (slug == null || slug.isBlank()) {
            throw new IllegalArgumentException("slug must not be blank");
        }

        var record = dsl.selectFrom(T_MATCH)
                .where(T_MATCH.FK_ROUND_ID.eq(roundId).and(T_MATCH.C_SLUG.eq(slug)))
                .fetchOne();

        return Optional.ofNullable(MAPPER.map(record));
    }

    @Override
    public Match save(Match match) {
        return match.getId() == null ? create(match) : update(match);
    }

    @Override
    public Optional<Match> findById(UUID id) {
        var record = dsl.selectFrom(T_MATCH).where(T_MATCH.PK_ID.eq(id)).fetchOne();
        return Optional.ofNullable(MAPPER.map(record));
    }

    @Override
    public Optional<Match> findByIdWithTeams(UUID id) {
        Optional<Match> matchOpt = findById(id);
        if (matchOpt.isEmpty()) {
            return Optional.empty();
        }

        Match match = matchOpt.get();
        MapTeams teams = loadTeams(Set.of(match.getHomeTeamId(), match.getAwayTeamId()));
        Team home = teams.byId().get(match.getHomeTeamId());
        Team away = teams.byId().get(match.getAwayTeamId());

        if (home != null && away != null) {
            match.setTeams(home, away);
        }

        return Optional.of(match);
    }

    @Override
    public List<Match> findByRoundIdWithTeams(UUID roundId) {
        List<Match> matches = findByRoundId(roundId);
        if (matches.isEmpty()) {
            return matches;
        }

        Set<UUID> teamIds = matches.stream()
                .flatMap(m -> Stream.of(m.getHomeTeamId(), m.getAwayTeamId()))
                .collect(Collectors.toSet());

        MapTeams teams = loadTeams(teamIds);
        for (Match match : matches) {
            Team home = teams.byId().get(match.getHomeTeamId());
            Team away = teams.byId().get(match.getAwayTeamId());
            if (home != null && away != null) {
                match.setTeams(home, away);
            }
        }

        return matches;
    }

    @Override
    public List<Match> findFinishedMatchesUpToRoundWithTeams(UUID seasonId, int roundPosition) {
        List<MatchRecord> records = dsl.select(T_MATCH.fields())
                .from(T_MATCH)
                .join(T_ROUND)
                .on(T_MATCH.FK_ROUND_ID.eq(T_ROUND.PK_ID))
                .where(T_ROUND.FK_SEASON_ID.eq(seasonId))
                .and(T_ROUND.C_POSITION.lessOrEqual(roundPosition))
                .and(T_MATCH.C_STATUS.eq(MatchStatus.FINISHED.name()))
                .orderBy(T_ROUND.C_POSITION.asc(), T_MATCH.C_KICK_OFF.asc())
                .fetchInto(MatchRecord.class);

        if (records.isEmpty()) {
            return List.of();
        }

        List<Match> matches = new ArrayList<>(records.size());
        Set<UUID> teamIds = new HashSet<>();
        for (MatchRecord record : records) {
            Match match = MAPPER.map(record);
            matches.add(match);
            teamIds.add(record.getHomeTeamId());
            teamIds.add(record.getAwayTeamId());
        }

        MapTeams teams = loadTeams(teamIds);
        for (Match match : matches) {
            Team home = teams.byId().get(match.getHomeTeamId());
            Team away = teams.byId().get(match.getAwayTeamId());
            if (home != null && away != null) {
                match.setTeams(home, away);
            }
        }

        return matches;
    }

    @Override
    public Map<String, List<Match>> findBySeasonAndRound(UUID seasonId, int round) {
        List<MatchRecord> records = dsl.select(T_MATCH.fields())
                .from(T_MATCH)
                .join(T_ROUND)
                .on(T_MATCH.FK_ROUND_ID.eq(T_ROUND.PK_ID))
                .where(T_ROUND.FK_SEASON_ID.eq(seasonId))
                .and(T_ROUND.C_POSITION.eq(round))
                .orderBy(T_MATCH.C_KICK_OFF.asc())
                .fetchInto(MatchRecord.class);

        if (records.isEmpty()) {
            return Map.of();
        }

        List<Match> matches = new ArrayList<>(records.size());
        Set<UUID> teamIds = new HashSet<>();
        for (MatchRecord record : records) {
            Match match = MAPPER.map(record);
            matches.add(match);
            teamIds.add(record.getHomeTeamId());
            teamIds.add(record.getAwayTeamId());
        }

        MapTeams teams = loadTeams(teamIds);
        for (Match match : matches) {
            Team home = teams.byId().get(match.getHomeTeamId());
            Team away = teams.byId().get(match.getAwayTeamId());
            if (home != null && away != null) {
                match.setTeams(home, away);
            }
        }

        Map<String, List<Match>> byTeam = new HashMap<>();
        for (Match match : matches) {
            if (!match.hasTeamsLoaded()) {
                continue;
            }
            byTeam.computeIfAbsent(match.getHomeTeam().getCode(), __ -> new ArrayList<>())
                    .add(match);
            byTeam.computeIfAbsent(match.getAwayTeam().getCode(), __ -> new ArrayList<>())
                    .add(match);
        }

        return byTeam;
    }

    @Override
    public Match create(Match model) {
        if (model.getId() != null) {
            throw new IllegalArgumentException(
                    String.format("Match.id must be null on create (received %s)", model.getId()));
        }

        UUID id = UUID.randomUUID();
        MatchRecord rec = dsl.newRecord(T_MATCH);
        rec.setId(id);
        copyModelToRecord(model, rec);
        rec.store();
        rec.refresh();
        return MAPPER.map(rec);
    }

    @Override
    public Match update(Match model) {
        MatchRecord rec =
                dsl.selectFrom(T_MATCH).where(T_MATCH.PK_ID.eq(model.getId())).fetchOne();
        if (rec == null) {
            throw new NoSuchElementException(String.format("Match with id %s not found", model.getId()));
        }
        copyModelToRecord(model, rec);
        rec.store();
        rec.refresh();
        return MAPPER.map(rec);
    }

    private static class MatchRecordMapper implements RecordMapper<MatchRecord, Match> {
        @Override
        public Match map(MatchRecord record) {
            if (record == null) {
                return null;
            }

            return Match.builder()
                    .id(record.getId())
                    .clientId(record.getClientId())
                    .roundId(record.getRoundId())
                    .homeTeamId(record.getHomeTeamId())
                    .awayTeamId(record.getAwayTeamId())
                    .slug(record.getSlug())
                    .status(parseStatus(record.getStatus()))
                    .kickOff(record.getKickOff())
                    .venue(record.getVenue())
                    .matchday(record.getMatchday())
                    .score(readScore(record.getScore()))
                    .wasPostponed(Boolean.TRUE.equals(record.getWasPostponed()))
                    .wasSuspended(Boolean.TRUE.equals(record.getWasSuspended()))
                    .createDate(record.getCreateDate())
                    .updateDate(record.getUpdateDate())
                    .build();
        }

        private static MatchStatus parseStatus(String raw) {
            if (raw == null) {
                return null;
            }
            return MatchStatus.valueOf(raw);
        }

        private static Score readScore(JSONB jsonb) {
            if (jsonb == null) {
                return null;
            }

            try {
                return OBJECT_MAPPER.readValue(jsonb.data(), new TypeReference<Score>() {});
            } catch (IOException e) {
                throw new IllegalStateException("Failed to deserialize match score JSON", e);
            }
        }
    }

    private static void copyModelToRecord(Match model, MatchRecord rec) {
        if (model == null || rec == null) return;

        rec.setClientId(model.getClientId());
        rec.setRoundId(model.getRoundId());
        rec.setHomeTeamId(model.getHomeTeamId());
        rec.setAwayTeamId(model.getAwayTeamId());
        rec.setSlug(model.getSlug());
        rec.setStatus(model.getStatus() == null ? null : model.getStatus().name());
        rec.setKickOff(model.getKickOff());
        rec.setVenue(model.getVenue());
        rec.setMatchday(model.getMatchday());

        rec.setWasPostponed(model.wasPostponed());
        rec.setWasSuspended(model.isWasSuspended());

        Score score = model.getScore();
        if (score == null) {
            rec.setScore(null);
        } else {
            try {
                String json = OBJECT_MAPPER.writeValueAsString(score);
                rec.setScore(JSONB.valueOf(json));
            } catch (IOException e) {
                throw new IllegalStateException("Failed to serialize match score JSON", e);
            }
        }
    }

    @Override
    public boolean existsBySeasonAndRoundAndTeams(UUID seasonId, UUID roundId, UUID homeTeamId, UUID awayTeamId) {
        return dsl.fetchExists(dsl.selectOne()
                .from(T_MATCH)
                .join(T_ROUND)
                .on(T_MATCH.FK_ROUND_ID.eq(T_ROUND.PK_ID))
                .where(T_ROUND.FK_SEASON_ID.eq(seasonId))
                .and(T_MATCH.FK_ROUND_ID.eq(roundId))
                .and(T_MATCH.FK_HOME_TEAM_ID.eq(homeTeamId))
                .and(T_MATCH.FK_AWAY_TEAM_ID.eq(awayTeamId)));
    }

    private record MapTeams(Map<UUID, Team> byId) {}

    private MapTeams loadTeams(Set<UUID> teamIds) {
        if (teamIds == null || teamIds.isEmpty()) {
            return new MapTeams(new HashMap<>());
        }

        Map<UUID, Team> byId =
                dsl.selectFrom(T_TEAM).where(T_TEAM.PK_ID.in(teamIds)).fetchMap(T_TEAM.PK_ID, this::mapTeam);

        return new MapTeams(byId);
    }

    private Team mapTeam(TeamRecord record) {
        if (record == null) {
            return null;
        }

        return Team.builder()
                .id(record.getId())
                .clientId(record.getClientId())
                .name(record.getName())
                .shortName(record.getShortName())
                .slug(record.getSlug() == null ? null : TeamSlug.of(record.getSlug()))
                .tla(record.getTla())
                .createDate(record.getCreateDate())
                .updateDate(record.getUpdateDate())
                .build();
    }
}
