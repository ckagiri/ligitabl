package com.ligitabl.seed.internal;

import static com.ligitabl.model.db.tables.TCompetition.T_COMPETITION;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.jooq.DSLContext;
import org.jooq.JSONB;

public class CompetitionSeeder extends AbstractSeeder<List<Map<String, Object>>> {

    private final ObjectMapper objectMapper;

    public CompetitionSeeder(DSLContext dsl, ObjectMapper objectMapper) {
        super(dsl);
        this.objectMapper = objectMapper;
    }

    @Override
    protected boolean isValidConfig(List<Map<String, Object>> competitions) {
        return competitions != null && !competitions.isEmpty();
    }

    @Override
    protected void performSeed(List<Map<String, Object>> competitions) {
        for (Map<String, Object> comp : competitions) {
            seedCompetition(comp);
        }
    }

    @Override
    protected String getSeederName() {
        return "competition";
    }

    private void seedCompetition(Map<String, Object> comp) {
        String code = (String) comp.get("code");
        String name = (String) comp.get("name");
        String slug = (String) comp.get("slug");

        if (slug == null || slug.isBlank()) {
            throw new IllegalArgumentException("Competition entry missing slug: " + comp);
        }

        JSONB phasesJson = null;
        Object phases = comp.get("phases");
        if (phases != null) {
            try {
                String json = objectMapper.writeValueAsString(phases);
                phasesJson = JSONB.valueOf(json);
            } catch (JsonProcessingException e) {
                throw new IllegalArgumentException(
                        "Failed to serialise phases for competition '" + slug + "'", e);
            }
        }

        int rowsAffected = dsl.insertInto(
                        T_COMPETITION,
                        T_COMPETITION.PK_ID,
                        T_COMPETITION.C_CODE,
                        T_COMPETITION.C_NAME,
                        T_COMPETITION.C_SLUG,
                        T_COMPETITION.C_PHASES)
                .values(UUID.randomUUID(), code, name, slug, phasesJson)
                .onConflict(T_COMPETITION.C_SLUG)
                .doNothing()
                .execute();

        if (rowsAffected > 0) {
            recordInsert();
        } else {
            recordSkip();
        }
    }
}

