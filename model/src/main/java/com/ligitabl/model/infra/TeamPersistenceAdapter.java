package com.ligitabl.model.infra;

import static com.ligitabl.model.db.tables.TTeam.T_TEAM;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.UUID;

import org.jooq.DSLContext;
import org.jooq.RecordMapper;
import org.jooq.impl.DSL;

import com.ligitabl.model.db.tables.records.TeamRecord;
import com.ligitabl.model.domain.Team;
import com.ligitabl.model.repo.TeamRepo;

import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class TeamPersistenceAdapter implements TeamRepo {
    private final DSLContext dsl;

    private static final TeamRecordMapper MAPPER = new TeamRecordMapper();

    @Override
    public Optional<Team> findById(UUID id) {
        var record = dsl.selectFrom(T_TEAM).where(T_TEAM.PK_ID.eq(id)).fetchOne();

        return Optional.ofNullable(MAPPER.map(record));
    }

    @Override
    public Team create(Team model) {
        // Use UpdatableRecord to leverage DB defaults/triggers (e.g.,
        // create_date/update_date)
        if (model.getId() != null) {
            throw new IllegalArgumentException(
                    String.format("Team.id must be null on create (received %s)", model.getId()));
        }
        UUID id = UUID.randomUUID();
        TeamRecord rec = dsl.newRecord(T_TEAM);
        rec.setId(id);
        copyModelToRecord(model, rec);
        // Persist first, then refresh to fetch DB-populated fields (portable across
        // dialects)
        rec.store();
        rec.refresh();
        return MAPPER.map(rec);
    }

    @Override
    public Team update(Team model) {
        // Fetch, mutate, and store() to trigger DB-side update hooks/defaults
        TeamRecord rec =
                dsl.selectFrom(T_TEAM).where(T_TEAM.PK_ID.eq(model.getId())).fetchOne();
        if (rec == null) {
            throw new NoSuchElementException(String.format("Team with id %s not found", model.getId()));
        }
        copyModelToRecord(model, rec);
        // Persist first, then refresh to ensure DB-side changes (e.g., update_date
        // triggers) are visible
        rec.store();
        rec.refresh();
        return MAPPER.map(rec);
    }

    @Override
    public void delete(UUID id) {
        dsl.deleteFrom(T_TEAM).where(T_TEAM.PK_ID.eq(id)).execute();
    }

    @Override
    public List<Team> findAll() {
        return dsl.selectFrom(T_TEAM).orderBy(T_TEAM.C_NAME.asc()).fetch().map(MAPPER::map);
    }

    @Override
    public Optional<Team> findBySlug(String slug) {
        var record = dsl.selectFrom(T_TEAM).where(T_TEAM.C_SLUG.eq(slug)).fetchOne();

        return Optional.ofNullable(MAPPER.map(record));
    }

    @Override
    public boolean isSlugInUseByAnotherTeam(String slug, UUID teamId) {
        return dsl.fetchExists(dsl.selectOne()
                .from(T_TEAM)
                .where(DSL.lower(T_TEAM.C_SLUG).eq(slug.toLowerCase()))
                .and(T_TEAM.PK_ID.ne(teamId)));
    }

    @Override
    public boolean existsBySlug(String slug) {
        return dsl.fetchExists(
                dsl.selectOne().from(T_TEAM).where(DSL.lower(T_TEAM.C_SLUG).eq(slug.toLowerCase())));
    }

    @Override
    public boolean existsById(UUID id) {
        return dsl.fetchExists(dsl.selectOne().from(T_TEAM).where(T_TEAM.PK_ID.eq(id)));
    }

    private static class TeamRecordMapper implements RecordMapper<TeamRecord, Team> {
        @Override
        public Team map(TeamRecord record) {
            if (record == null) {
                return null;
            }

            return Team.builder()
                    .id(record.getId())
                    .name(record.getName())
                    .shortName(record.getShortName())
                    .slug(record.getSlug())
                    .tla(record.getTla())
                    .createDate(record.getCreateDate())
                    .updateDate(record.getUpdateDate())
                    .build();
        }
    }

    // Centralise mutable field mapping from domain -> record to avoid repetition as
    // fields grow
    private static void copyModelToRecord(Team model, TeamRecord rec) {
        if (model == null || rec == null) return;
        rec.setName(model.getName());
        rec.setShortName(model.getShortName());
        rec.setSlug(model.getSlug());
        rec.setTla(model.getTla());
    }
}
