package com.ligitabl.model.repo;

import com.ligitabl.model.Team;
import com.ligitabl.model.dao.TeamDao;

import java.util.List;
import java.util.UUID;

public class TeamRepo implements TeamDao {

    @Override
    public Team findById(UUID uuid) {
        return null;
    }

    @Override
    public Team create(Team model) {
        return null;
    }

    @Override
    public Team update(Team model) {
        return null;
    }

    @Override
    public void delete(UUID uuid) {

    }

    @Override
    public List<Team> findAll() {
        return List.of();
    }
}
