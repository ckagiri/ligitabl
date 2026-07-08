package com.ligitabl.model.repo;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import com.ligitabl.model.domain.Entry;

public interface EntryRepo {
    Entry save(Entry entry);

    Optional<Entry> findByUserAndContest(UUID userId, UUID contestId);

    List<Entry> findByContestId(UUID contestId);

    List<Entry> findByUserId(UUID userId);

    int countActiveByContestId(UUID contestId);

    /**
     * Active member counts for multiple contests in one query. Contest ids with no active
     * entries are omitted from the result map (treat missing as 0).
     */
    Map<UUID, Integer> countActiveByContestIds(List<UUID> contestIds);

    void softRemove(UUID userId, UUID contestId, int removedAtRound);

    /**
     * Copies every active (not removed) entry from {@code fromContestId} into {@code toContestId},
     * joined at {@code joinedAtRound}. Used by contest renewal to carry members over into the new
     * contest in one statement.
     */
    void copyActiveEntries(UUID fromContestId, UUID toContestId, int joinedAtRound);

    boolean hasAnyScore(UUID userId, UUID contestId);

    void deleteByUserAndContest(UUID userId, UUID contestId);

    void deleteByContestId(UUID contestId);
}
