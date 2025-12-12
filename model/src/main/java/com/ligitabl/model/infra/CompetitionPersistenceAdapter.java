package com.ligitabl.model.infra;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.ligitabl.model.domain.Competition;
import com.ligitabl.model.domain.CompetitionSlug;
import com.ligitabl.model.repo.CompetitionRepo;

/**
 * Temporary stub implementation until a real JOOQ-backed adapter is added.
 */
public class CompetitionPersistenceAdapter implements CompetitionRepo {

    @Override
    public Optional<Competition> findById(UUID id) {
        return Optional.empty();
    }

    @Override
    public List<Competition> findAll() {
        return Collections.emptyList();
    }

    @Override
    public Optional<Competition> findBySlug(CompetitionSlug slug) {
        return Optional.empty();
    }

    @Override
    public boolean existsById(UUID id) {
        return false;
    }
}
