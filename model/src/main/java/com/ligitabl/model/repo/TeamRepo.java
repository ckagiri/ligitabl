package com.ligitabl.model.repo;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import com.ligitabl.model.domain.Team;
import com.ligitabl.model.domain.TeamSlug;

public interface TeamRepo extends BaseCrudRepo<Team, UUID> {
    List<Team> findAll();

    Optional<Team> findBySlug(TeamSlug slug);

    Optional<Team> findByClientId(Integer clientId);

    List<Team> findAllByIds(Set<UUID> ids);

    boolean existsBySlug(TeamSlug slug);

    boolean existsById(UUID id);

    boolean isSlugInUseByAnotherTeam(TeamSlug slug, UUID teamId);
}
