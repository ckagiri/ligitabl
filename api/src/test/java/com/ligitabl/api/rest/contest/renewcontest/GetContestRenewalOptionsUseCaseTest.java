package com.ligitabl.api.rest.contest.renewcontest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.ligitabl.api.web.contest.shared.ContestSupport;
import com.ligitabl.model.domain.*;
import com.ligitabl.model.repo.*;

/**
 * Phases mirror a season's S1-S8 / Q1-Q4 structure, one round per sprint: S1=round1 ...
 * S8=round8, Q1=S1+S2, Q2=S3+S4, Q3=S5+S6, Q4=S7+S8.
 */
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

    @Mock
    ContestSupport contestSupport;

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
        useCase = new GetContestRenewalOptionsUseCase(
                contestRepo, seasonRepo, roundRepo, competitionRepo, entryRepo, contestSupport);
        lenient().when(contestSupport.isOpenForJoining(any(), any(), any())).thenReturn(true);

        userId = UUID.randomUUID();
        competitionId = UUID.randomUUID();
        seasonId = UUID.randomUUID();
        currentRoundId = UUID.randomUUID();
        phases = buildPhases();

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
                .maxRounds(8)
                .currentRoundId(currentRoundId)
                .build();
    }

    /** Stubs the current round position, used to satisfy the current-season renewal timing gate. */
    private void stubCurrentRoundPosition(int position) {
        when(roundRepo.findPosition(currentRoundId)).thenReturn(position);
    }

    private static List<RoundSpan> buildPhases() {
        List<RoundSpan> sprints = List.of(
                sprint("S1", 1, 1),
                sprint("S2", 2, 2),
                sprint("S3", 3, 3),
                sprint("S4", 4, 4),
                sprint("S5", 5, 5),
                sprint("S6", 6, 6),
                sprint("S7", 7, 7),
                sprint("S8", 8, 8));
        List<RoundSpan> quarters = List.of(
                quarter("Q1", 1, 2), quarter("Q2", 3, 4), quarter("Q3", 5, 6), quarter("Q4", 7, 8));
        return java.util.stream.Stream.concat(sprints.stream(), quarters.stream()).toList();
    }

    private static RoundSpan sprint(String code, int from, int to) {
        return RoundSpan.builder().code(code).name(code).type(PhaseType.SPRINT).from(from).to(to).build();
    }

    private static RoundSpan quarter(String code, int from, int to) {
        return RoundSpan.builder().code(code).name(code).type(PhaseType.QUARTER).from(from).to(to).build();
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
        Contest contest = contest(1, 2); // S1 -> S2 (Q1); own last sprint is S2
        when(contestRepo.findById(contest.getId())).thenReturn(Optional.of(contest));
        when(seasonRepo.findById(seasonId)).thenReturn(Optional.of(season));
        when(competitionRepo.findById(competitionId)).thenReturn(Optional.of(competition));
        when(seasonRepo.findActiveSeason(competitionId)).thenReturn(Optional.of(season));
        stubCurrentRoundPosition(2); // S2 — final leg of the original underway, timing gate met
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
        Contest contest = contest(1, 2); // S1 -> S2 (Q1); own last sprint is S2
        when(contestRepo.findById(contest.getId())).thenReturn(Optional.of(contest));
        when(seasonRepo.findById(seasonId)).thenReturn(Optional.of(season));
        when(competitionRepo.findById(competitionId)).thenReturn(Optional.of(competition));
        when(seasonRepo.findActiveSeason(competitionId)).thenReturn(Optional.of(season));
        stubCurrentRoundPosition(1); // S1 — final leg (S2) not underway yet
        when(entryRepo.countActiveByContestId(contest.getId())).thenReturn(5);

        var result = useCase.execute(contest.getId(), userId).get();

        assertThat(result.visible()).isTrue();
        assertThat(result.enabled()).isFalse();
    }

    @Test
    void currentSeason_fullSeason_notRenewable() {
        Contest contest = contest(1, 8);
        when(contestRepo.findById(contest.getId())).thenReturn(Optional.of(contest));
        when(seasonRepo.findById(seasonId)).thenReturn(Optional.of(season));
        when(competitionRepo.findById(competitionId)).thenReturn(Optional.of(competition));
        when(seasonRepo.findActiveSeason(competitionId)).thenReturn(Optional.of(season));

        var result = useCase.execute(contest.getId(), userId).get();

        assertThat(result.visible()).isFalse();
    }

    @Test
    void notPrivate_hidden() {
        Contest contest = contest(1, 2);
        contest.setPrivate(false);
        when(contestRepo.findById(contest.getId())).thenReturn(Optional.of(contest));

        var result = useCase.execute(contest.getId(), userId).get();

        assertThat(result.visible()).isFalse();
    }

    @Test
    void alreadyRenewed_notRenewable() {
        Contest contest = contest(1, 2);
        contest.setRenewedIntoContestId(UUID.randomUUID());
        when(contestRepo.findById(contest.getId())).thenReturn(Optional.of(contest));
        when(seasonRepo.findById(seasonId)).thenReturn(Optional.of(season));
        when(competitionRepo.findById(competitionId)).thenReturn(Optional.of(competition));

        var result = useCase.execute(contest.getId(), userId).get();

        assertThat(result.visible()).isFalse();
    }

    @Test
    void notOwner_returnsError() {
        Contest contest = contest(1, 2);
        when(contestRepo.findById(contest.getId())).thenReturn(Optional.of(contest));

        var result = useCase.execute(contest.getId(), UUID.randomUUID());

        assertThat(result.isLeft()).isTrue();
        assertThat(result.getLeft()).isInstanceOf(RenewContestError.NotOwner.class);
    }

    @Test
    void pastSeason_partialOriginal_defaultsToEndOfQ1() {
        Contest contest = contest(7, 8); // S7 -> S8 in the past season
        when(contestRepo.findById(contest.getId())).thenReturn(Optional.of(contest));
        when(seasonRepo.findById(seasonId)).thenReturn(Optional.of(season));
        when(competitionRepo.findById(competitionId)).thenReturn(Optional.of(competition));

        Season activeSeason =
                Season.builder().id(UUID.randomUUID()).competitionId(competitionId).build();
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
        Contest contest = contest(1, 8);
        when(contestRepo.findById(contest.getId())).thenReturn(Optional.of(contest));
        when(seasonRepo.findById(seasonId)).thenReturn(Optional.of(season));
        when(competitionRepo.findById(competitionId)).thenReturn(Optional.of(competition));

        Season activeSeason =
                Season.builder().id(UUID.randomUUID()).competitionId(competitionId).build();
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
        Contest contest = contest(1, 2);
        when(contestRepo.findById(contest.getId())).thenReturn(Optional.of(contest));
        when(seasonRepo.findById(seasonId)).thenReturn(Optional.of(season));
        when(competitionRepo.findById(competitionId)).thenReturn(Optional.of(competition));
        when(seasonRepo.findActiveSeason(competitionId)).thenReturn(Optional.empty());

        var result = useCase.execute(contest.getId(), userId).get();

        assertThat(result.visible()).isFalse();
    }
}
