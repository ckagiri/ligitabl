package com.ligitabl.model.repo;

import com.ligitabl.model.domain.Team;

import java.util.List;
import java.util.UUID;

public interface TeamRepo extends BaseCrudRepo<Team, UUID> {
    List<Team> findAll();
}
