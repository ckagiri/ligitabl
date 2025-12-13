package com.ligitabl.seed.internal;

import static com.ligitabl.model.db.tables.TTeam.T_TEAM;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.jooq.DSLContext;

public class TeamSeeder extends AbstractSeeder<List<Map<String, Object>>> {

    public TeamSeeder(DSLContext dsl) {
        super(dsl);
    }

    @Override
    protected boolean isValidConfig(List<Map<String, Object>> config) {
        return config != null && !config.isEmpty();
    }

    @Override
    protected void performSeed(List<Map<String, Object>> teams) {
        for (Map<String, Object> team : teams) {
            seedTeam(team);
        }
    }

    @Override
    protected String getSeederName() {
        return "team";
    }

    private void seedTeam(Map<String, Object> team) {
        Object nameRaw = team.get("name");
        Object shortNameRaw = team.get("shortName");
        Object slugRaw = team.get("slug");
        Object tlaRaw = team.get("tla");

        String name = nameRaw == null ? null : nameRaw.toString();
        String shortName = shortNameRaw == null ? null : shortNameRaw.toString();
        String slug = slugRaw == null ? null : slugRaw.toString();
        String tla = tlaRaw == null ? null : tlaRaw.toString();

        if (slug == null || slug.isBlank()) {
            throw new IllegalArgumentException("Team entry missing slug: " + team);
        }
        if (tla == null || tla.isBlank()) {
            throw new IllegalArgumentException("Team entry missing tla: " + team);
        }

        // Enforce 3-character TLA as per schema (char(3))
        if (tla.length() > 3) {
            tla = tla.substring(0, 3);
        }

        if (teamExists(slug)) {
            recordSkip();
            return;
        }

        int rowsAffected = dsl.insertInto(
                        T_TEAM,
                        T_TEAM.PK_ID,
                        T_TEAM.C_NAME,
                        T_TEAM.C_SHORT_NAME,
                        T_TEAM.C_SLUG,
                        T_TEAM.C_TLA)
                .values(UUID.randomUUID(), name, shortName, slug, tla)
                .execute();

        if (rowsAffected > 0) {
            recordInsert();
        } else {
            recordSkip();
        }
    }

    private boolean teamExists(String slug) {
        return dsl.fetchExists(
                dsl.selectOne().from(T_TEAM).where(T_TEAM.C_SLUG.eq(slug)));
    }
}
