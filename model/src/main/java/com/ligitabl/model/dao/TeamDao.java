package com.ligitabl.model.dao;

import com.ligitabl.model.Team;

import java.util.List;
import java.util.UUID;

public interface TeamDao extends BaseCrudDao<Team, UUID> {
    List<Team> findAll();
}
