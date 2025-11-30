package com.ligitabl.model.repo;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.ligitabl.model.domain.Season;
import com.ligitabl.model.domain.SeasonSlug;

public interface SeasonRepo {
    Optional<Season> findById(UUID id);

    List<Season> findAll();

    Optional<Season> findBySlug(SeasonSlug slug);

    boolean existsById(UUID id);
}
