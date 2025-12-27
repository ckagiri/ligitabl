package com.ligitabl.model.repo;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.ligitabl.model.domain.Entry;

public interface EntryRepo {
    Entry save(Entry entry);

    Optional<Entry> findByUserAndContest(UUID userId, UUID contestId);

    List<Entry> findByContestId(UUID contestId);

    List<Entry> findByUserId(UUID userId);
}
