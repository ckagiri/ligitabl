package com.ligitabl.api.rest.contest.renewcontest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.ligitabl.model.domain.*;
import com.ligitabl.model.repo.*;

/** Uses the real Premier League phase structure (see {@link CompetitionPhaseFixtures}). */
@ExtendWith(MockitoExtension.class)
class GetContestRenewalOptionsUseCaseTest {

    @Mock
    ContestRepo contestRepo;

    @Mock
    SeasonRepo seasonRepo;

    @Mock
    RoundRepo roundRepo;

    @Mock
    CompetitionRepo competitionRepo;

    @Mock
    EntryRepo entryRepo;

    private GetContestRenewalOptionsUseCase useCase;

    private UUID userId;
    private UUID competitionId;
    private UUID seasonId;
    private UUID currentRoundId;
    private List<RoundSpan> phases;
    private Competition competition;
    private Season season;

    @BeforeEach
    void setUp() {
        useCase = new GetContestRenewalOptionsUseCase(contestRepo, seasonRepo, roundRepo, competitionRepo, entryRepo);

        userId = UUID.randomUUID();
        competitionId = UUID.randomUUID();
        seasonId = UUID.randomUUID();
        currentRoundId = UUID.randomUUID();
        phases = CompetitionPhaseFixtures.phases();

        competition = Competition.builder()
                .id(competitionId)
                .name("Premier League")
                .slug(CompetitionSlug.of("premier-league"))
                .code("PL")
                .phases(phases)
                .build();

        season = Season.builder()
                .id(seasonId)
                .competitionId(competitionId)
                .name("2025/26")
                .slug(SeasonSlug.of("2025-26"))
                .clientId(1)
                .maxRounds(38)
                .currentRoundId(currentRoundId)
                .build();
    }

    /** Stubs the current round position, used to satisfy the current-season renewal timing gate. */
    private void stubCurrentRoundPosition(int position) {
        when(roundRepo.findPosition(currentRoundId)).thenReturn(position);
    }

    private Contest contest(int fromRoundPosition, int toRoundPosition) {
        return Contest.builder()
                .id(UUID.randomUUID())
                .seasonId(seasonId)
                .name("Office Rivals")
                .isPrivate(true)
                .isOpen(true)
                .joinCode("CODE1")
                .fromRoundPosition(fromRoundPosition)
                .toRoundPosition(toRoundPosition)
                .maxEntries(10)
                .ownerId(userId)
                .build();
    }

    @Test
    void currentSeason_renewableAndTimingMet_returnsFromAndDefaultTo() {
        Contest contest = contest(1, 9); // S1 -> S2 (Q1); own last sprint is S2
        when(contestRepo.findById(contest.getId())).thenReturn(Optional.of(contest));
        when(seasonRepo.findById(seasonId)).thenReturn(Optional.of(season));
        when(competitionRepo.findById(competitionId)).thenReturn(Optional.of(competition));
        when(seasonRepo.findActiveSeason(competitionId)).thenReturn(Optional.of(season));
        stubCurrentRoundPosition(5); // GW5 — S2 (final leg) underway, timing gate met
        when(entryRepo.countActiveByContestId(contest.getId())).thenReturn(5);

        var result = useCase.execute(contest.getId(), userId).get();

        assertThat(result.visible()).isTrue();
        assertThat(result.enabled()).isTrue();
        assertThat(result.fromCode()).isEqualTo("S3");
        assertThat(result.defaultToCode()).isEqualTo("S4");
        assertThat(result.toOptionCodes()).containsExactly("S3", "S4", "S6", "S8");
        assertThat(result.activeMemberCount()).isEqualTo(5);
    }

    @Test
    void currentSeason_visibleButTimingNotYetMet_disabled() {
        Contest contest = contest(1, 9); // S1 -> S2 (Q1); own last sprint is S2
        when(contestRepo.findById(contest.getId())).thenReturn(Optional.of(contest));
        when(seasonRepo.findById(seasonId)).thenReturn(Optional.of(season));
        when(competitionRepo.findById(competitionId)).thenReturn(Optional.of(competition));
        when(seasonRepo.findActiveSeason(competitionId)).thenReturn(Optional.of(season));
        stubCurrentRoundPosition(1); // GW1, within S1 — final leg (S2) not underway yet
        when(entryRepo.countActiveByContestId(contest.getId())).thenReturn(5);

        var result = useCase.execute(contest.getId(), userId).get();

        assertThat(result.visible()).isTrue();
        assertThat(result.enabled()).isFalse();
    }

    @Test
    void currentSeason_fullSeason_notRenewable() {
        Contest contest = contest(1, 38);
        when(contestRepo.findById(contest.getId())).thenReturn(Optional.of(contest));
        when(seasonRepo.findById(seasonId)).thenReturn(Optional.of(season));
        when(competitionRepo.findById(competitionId)).thenReturn(Optional.of(competition));
        when(seasonRepo.findActiveSeason(competitionId)).thenReturn(Optional.of(season));

        var result = useCase.execute(contest.getId(), userId).get();

        assertThat(result.visible()).isFalse();
    }

    @Test
    void notPrivate_hidden() {
        Contest contest = contest(1, 9);
        contest.setPrivate(false);
        when(contestRepo.findById(contest.getId())).thenReturn(Optional.of(contest));

        var result = useCase.execute(contest.getId(), userId).get();

        assertThat(result.visible()).isFalse();
    }

    @Test
    void alreadyRenewed_notRenewable() {
        Contest contest = contest(1, 9);
        contest.setRenewedIntoContestId(UUID.randomUUID());
        when(contestRepo.findById(contest.getId())).thenReturn(Optional.of(contest));
        when(seasonRepo.findById(seasonId)).thenReturn(Optional.of(season));
        when(competitionRepo.findById(competitionId)).thenReturn(Optional.of(competition));

        var result = useCase.execute(contest.getId(), userId).get();

        assertThat(result.visible()).isFalse();
    }

    @Test
    void notOwner_returnsError() {
        Contest contest = contest(1, 9);
        when(contestRepo.findById(contest.getId())).thenReturn(Optional.of(contest));

        var result = useCase.execute(contest.getId(), UUID.randomUUID());

        assertThat(result.isLeft()).isTrue();
        assertThat(result.getLeft()).isInstanceOf(RenewContestError.NotOwner.class);
    }

    @Test
    void pastSeason_partialOriginal_defaultsToEndOfQ1() {
        Contest contest = contest(30, 38); // S7 -> S8 in the past season
        when(contestRepo.findById(contest.getId())).thenReturn(Optional.of(contest));
        when(seasonRepo.findById(seasonId)).thenReturn(Optional.of(season));
        when(competitionRepo.findById(competitionId)).thenReturn(Optional.of(competition));

        Season activeSeason = Season.builder()
                .id(UUID.randomUUID())
                .competitionId(competitionId)
                .build();
        when(seasonRepo.findActiveSeason(competitionId)).thenReturn(Optional.of(activeSeason));
        when(entryRepo.countActiveByContestId(contest.getId())).thenReturn(3);

        var result = useCase.execute(contest.getId(), userId).get();

        assertThat(result.visible()).isTrue();
        assertThat(result.fromCode()).isEqualTo("S1");
        assertThat(result.defaultToCode()).isEqualTo("S2");
        assertThat(result.toOptionCodes()).containsExactly("S1", "S2", "S4", "S6", "S8");
    }

    @Test
    void pastSeason_fullSeasonOriginal_onlyS8Offered() {
        Contest contest = contest(1, 38);
        when(contestRepo.findById(contest.getId())).thenReturn(Optional.of(contest));
        when(seasonRepo.findById(seasonId)).thenReturn(Optional.of(season));
        when(competitionRepo.findById(competitionId)).thenReturn(Optional.of(competition));

        Season activeSeason = Season.builder()
                .id(UUID.randomUUID())
                .competitionId(competitionId)
                .build();
        when(seasonRepo.findActiveSeason(competitionId)).thenReturn(Optional.of(activeSeason));
        when(entryRepo.countActiveByContestId(contest.getId())).thenReturn(3);

        var result = useCase.execute(contest.getId(), userId).get();

        assertThat(result.visible()).isTrue();
        assertThat(result.fromCode()).isEqualTo("S1");
        assertThat(result.defaultToCode()).isEqualTo("S8");
        assertThat(result.toOptionCodes()).containsExactly("S8");
    }

    @Test
    void pastSeason_noActiveSeason_notRenewable() {
        Contest contest = contest(1, 9);
        when(contestRepo.findById(contest.getId())).thenReturn(Optional.of(contest));
        when(seasonRepo.findById(seasonId)).thenReturn(Optional.of(season));
        when(competitionRepo.findById(competitionId)).thenReturn(Optional.of(competition));
        when(seasonRepo.findActiveSeason(competitionId)).thenReturn(Optional.empty());

        var result = useCase.execute(contest.getId(), userId).get();

        assertThat(result.visible()).isFalse();
    }
}
