package com.ligitabl.model.infra;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.ligitabl.model.domain.Season;
import com.ligitabl.model.domain.SeasonSlug;
import com.ligitabl.model.repo.SeasonRepo;

/**
 * Temporary stub implementation until a real JOOQ-backed adapter is added.
 */
public class SeasonPersistenceAdapter implements SeasonRepo {

    @Override
    public Optional<Season> findById(UUID id) {
        return Optional.empty();
    }

    @Override
    public List<Season> findAll() {
        return Collections.emptyList();
    }

    @Override
    public Optional<Season> findBySlug(SeasonSlug slug) {
        return Optional.empty();
    }

    @Override
    public boolean existsById(UUID id) {
        return false;
    }
}
