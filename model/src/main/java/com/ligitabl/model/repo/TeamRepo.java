package com.ligitabl.model.repo;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.ligitabl.model.domain.Team;

public interface TeamRepo extends BaseCrudRepo<Team, UUID> {
    List<Team> findAll();

    Optional<Team> findBySlug(String slug);

    boolean existsBySlug(String slug);

    boolean existsById(UUID id);

    boolean isSlugInUseByAnotherTeam(String slug, UUID teamId);
}
