package com.ligitabl.model.repo;

import com.ligitabl.model.domain.Entry;

import java.util.List;
import java.util.Optional;

public interface EntryRepo {
    Entry save(Entry entry);
    Optional<Entry> findByUserAndContest(Long userId, Long contestId);
    List<Entry> findByContestId(Long contestId);
    List<Entry> findByUserId(Long userId);
}
