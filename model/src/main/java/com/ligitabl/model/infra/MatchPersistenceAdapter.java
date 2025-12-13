package com.ligitabl.model.infra;

import static com.ligitabl.model.db.tables.TMatch.T_MATCH;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

import org.jooq.DSLContext;
import org.jooq.JSONB;
import org.jooq.RecordMapper;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ligitabl.model.db.tables.records.MatchRecord;
import com.ligitabl.model.domain.Match;
import com.ligitabl.model.domain.MatchStatus;
import com.ligitabl.model.domain.Score;
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
}
