package com.ligitabl.model.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;

import org.junit.jupiter.api.Test;

class ContestTest {

    @Test
    void isOwnedByReturnsTrueWhenOwnerIdMatches() {
        UUID ownerId = UUID.randomUUID();
        Contest contest = Contest.builder()
                .seasonId(UUID.randomUUID())
                .name("Test")
                .ownerId(ownerId)
                .isOpen(true)
                .build();

        assertThat(contest.isOwnedBy(ownerId)).isTrue();
    }

    @Test
    void isOwnedByReturnsFalseWhenOwnerIdDiffers() {
        Contest contest = Contest.builder()
                .seasonId(UUID.randomUUID())
                .name("Test")
                .ownerId(UUID.randomUUID())
                .isOpen(true)
                .build();

        assertThat(contest.isOwnedBy(UUID.randomUUID())).isFalse();
    }

    @Test
    void isOwnedByReturnsFalseWhenOwnerIdIsNull() {
        Contest contest = Contest.builder()
                .seasonId(UUID.randomUUID())
                .name("Main League")
                .isPrivate(false)
                .isOpen(true)
                .build();

        assertThat(contest.isOwnedBy(UUID.randomUUID())).isFalse();
    }

    @Test
    void isMainReturnsTrueForPublicContestStartingAtRoundOne() {
        Contest contest = Contest.builder()
                .seasonId(UUID.randomUUID())
                .name("Main League")
                .isPrivate(false)
                .fromRoundPosition(1)
                .build();

        assertThat(contest.isMain()).isTrue();
    }

    @Test
    void isMainReturnsFalseForPrivateContest() {
        Contest contest = Contest.builder()
                .seasonId(UUID.randomUUID())
                .name("Private")
                .isPrivate(true)
                .fromRoundPosition(1)
                .build();

        assertThat(contest.isMain()).isFalse();
    }
}
