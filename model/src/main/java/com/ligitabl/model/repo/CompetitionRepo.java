package com.ligitabl.model.repo;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.ligitabl.model.domain.Competition;
import com.ligitabl.model.domain.CompetitionSlug;

public interface CompetitionRepo {
    Optional<Competition> findById(UUID id);

    List<Competition> findAll();

    Optional<Competition> findBySlug(CompetitionSlug slug);

    default Optional<Competition> findBySlug(String slug) {
        return findBySlug(CompetitionSlug.of(slug));
    }

    boolean existsById(UUID id);
}
