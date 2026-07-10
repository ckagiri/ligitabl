package com.ligitabl.model.repo;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import com.ligitabl.model.domain.Team;
import com.ligitabl.model.domain.TeamRank;
import com.ligitabl.model.domain.TeamSlug;

public interface TeamRepo extends BaseCrudRepo<Team, UUID> {
    List<Team> findAll();

    Optional<Team> findByCode(String code);

    Optional<Team> findBySlug(TeamSlug slug);

    Optional<Team> findByClientId(Integer clientId);

    List<Team> findAllByIds(Set<UUID> ids);

    List<Team> findAllByCodes(Set<String> codes);

    Map<String, Team> findAllTeamsByCode(List<TeamRank> rankings);

    boolean existsBySlug(TeamSlug slug);

    boolean existsById(UUID id);

    boolean isSlugInUseByAnotherTeam(TeamSlug slug, UUID teamId);
}
