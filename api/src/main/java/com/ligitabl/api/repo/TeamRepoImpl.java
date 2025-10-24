package com.ligitabl.api.repo;

import com.ligitabl.model.domain.Team;
import com.ligitabl.model.repo.TeamRepo;
import lombok.RequiredArgsConstructor;
import org.jooq.DSLContext;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

import static com.ligitabl.db.tables.TTeam.T_TEAM;

@Repository
@RequiredArgsConstructor
public class TeamRepoImpl implements TeamRepo {

    private final DSLContext dsl;

    @Override
    public Team findById(UUID id) {
        var r = dsl
                .selectFrom(T_TEAM)
                .where(T_TEAM.PK_ID.eq(id))
                .fetchOne();
        if (r == null) return null;
        Team t = Team.builder()
                .name(r.get(T_TEAM.C_NAME))
                .shortName(r.get(T_TEAM.C_SHORT_NAME))
                .build();
        t.setId(r.get(T_TEAM.PK_ID));
        return t;
    }

    @Override
    public Team create(Team model) {
        UUID id = model.getId() != null ? model.getId() : UUID.randomUUID();
        dsl.insertInto(T_TEAM)
                .set(T_TEAM.PK_ID, id)
                .set(T_TEAM.C_NAME, model.getName())
                .set(T_TEAM.C_SHORT_NAME, model.getShortName())
                .execute();
        model.setId(id);
        return model;
    }

    @Override
    public Team update(Team model) {
        dsl.update(T_TEAM)
                .set(T_TEAM.C_NAME, model.getName())
                .set(T_TEAM.C_SHORT_NAME, model.getShortName())
                .where(T_TEAM.PK_ID.eq(model.getId()))
                .execute();
        return model;
    }

    @Override
    public void delete(UUID uuid) {
        dsl.deleteFrom(T_TEAM)
                .where(T_TEAM.PK_ID.eq(uuid))
                .execute();
    }

    @Override
    public List<Team> findAll() {
        return dsl
                .selectFrom(T_TEAM)
                .orderBy(T_TEAM.C_NAME.asc())
                .fetch()
                .map(r -> {
                    Team t2 = Team.builder()
                            .name(r.get(T_TEAM.C_NAME))
                            .shortName(r.get(T_TEAM.C_SHORT_NAME))
                            .build();
                    t2.setId(r.get(T_TEAM.PK_ID));
                    return t2;
                });
    }
}
