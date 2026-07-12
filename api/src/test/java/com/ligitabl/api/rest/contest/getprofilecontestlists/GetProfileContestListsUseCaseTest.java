package com.ligitabl.api.rest.contest.getprofilecontestlists;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.ligitabl.api.config.CompetitionDefaults;
import com.ligitabl.api.rest.contest.renewcontest.GetContestRenewalOptionsResult;
import com.ligitabl.api.rest.contest.renewcontest.GetContestRenewalOptionsUseCase;
import com.ligitabl.api.rest.contest.shared.ContestRankResolver;
import com.ligitabl.api.rest.round.shared.RoundSupport;
import com.ligitabl.api.shared.Either;
import com.ligitabl.api.web.contest.shared.ContestSupport;
import com.ligitabl.model.domain.Competition;
import com.ligitabl.model.domain.CompetitionPhaseFixtures;
import com.ligitabl.model.domain.CompetitionSlug;
import com.ligitabl.model.domain.Round;
import com.ligitabl.model.domain.Season;
import com.ligitabl.model.domain.SeasonSlug;
import com.ligitabl.model.repo.CompetitionRepo;
import com.ligitabl.model.repo.ContestRepo;
import com.ligitabl.model.repo.EntryRepo;
import com.ligitabl.model.repo.SeasonRepo;

/** Uses the real Premier League phase structure (see {@link CompetitionPhaseFixtures}). */
@ExtendWith(MockitoExtension.class)
class GetProfileContestListsUseCaseTest {

    @Mock
    CompetitionRepo competitionRepo;

    @Mock
    SeasonRepo seasonRepo;

    @Mock
    ContestRepo contestRepo;

    @Mock
    EntryRepo entryRepo;

    @Mock
    ContestRankResolver contestRankResolver;

    @Mock
    ContestSupport contestSupport;

    @Mock
    RoundSupport roundSupport;

    @Mock
    GetContestRenewalOptionsUseCase getContestRenewalOptionsUseCase;

    private GetProfileContestListsUseCase useCase;
    private UUID userId;
    private UUID seasonId;

    @BeforeEach
    void setUp() {
        CompetitionDefaults competitionDefaults = new CompetitionDefaults("premier-league");
        useCase = new GetProfileContestListsUseCase(
                competitionDefaults,
                competitionRepo,
                seasonRepo,
                contestRepo,
                entryRepo,
                contestRankResolver,
                contestSupport,
                roundSupport,
                getContestRenewalOptionsUseCase);

        userId = UUID.randomUUID();
        seasonId = UUID.randomUUID();

        Competition competition = Competition.builder()
                .id(UUID.randomUUID())
                .name("Premier League")
                .slug(CompetitionSlug.of("premier-league"))
                .code("PL")
                .phases(CompetitionPhaseFixtures.phases())
                .build();
        when(competitionRepo.findBySlug("premier-league")).thenReturn(Optional.of(competition));
        when(seasonRepo.findActiveSeason("premier-league")).thenReturn(Optional.empty());
        when(entryRepo.countActiveByContestIds(any())).thenReturn(Map.of());
    }

    private ContestRepo.UserContestView view(int from, int to) {
        return new ContestRepo.UserContestView(
                UUID.randomUUID(), "Homeboyz", seasonId, "2026/27", false, from, to, true, true, false);
    }

    private ContestRepo.UserContestView view(int from, int to, boolean isPrivate, boolean isOwner) {
        return new ContestRepo.UserContestView(
                UUID.randomUUID(), "Homeboyz", seasonId, "2026/27", false, from, to, isPrivate, true, isOwner);
    }

    @Test
    void periodLabel_multiSprintWindow_appearsInSummary() {
        when(contestRepo.countContestsByUserId(userId, null, true)).thenReturn(1);
        when(contestRepo.countContestsByUserId(userId, null, false)).thenReturn(0);
        when(contestRepo.findContestsByUserId(userId, null, true, 10, 0)).thenReturn(List.of(view(10, 29))); // Q2-3
        when(contestRepo.findContestsByUserId(userId, null, false, 10, 0)).thenReturn(List.of());

        var result = useCase.execute(new GetProfileContestListsQuery(userId, 1, 1));

        assertThat(result.activeContests()).hasSize(1);
        assertThat(result.activeContests().get(0).seasonName()).isEqualTo("2026/27");
        assertThat(result.activeContests().get(0).periodLabel()).isEqualTo("Q2-3");
    }

    @Test
    void periodLabel_singleSprintWindow_isSprintCode() {
        when(contestRepo.countContestsByUserId(userId, null, true)).thenReturn(1);
        when(contestRepo.countContestsByUserId(userId, null, false)).thenReturn(0);
        when(contestRepo.findContestsByUserId(userId, null, true, 10, 0)).thenReturn(List.of(view(10, 14))); // S3
        when(contestRepo.findContestsByUserId(userId, null, false, 10, 0)).thenReturn(List.of());

        var result = useCase.execute(new GetProfileContestListsQuery(userId, 1, 1));

        assertThat(result.activeContests().get(0).periodLabel()).isEqualTo("S3");
    }

    @Test
    void activeTab_multiplePrivateContests_resolveCurrentRoundOnce_andDeriveStatusPerRow() {
        Season activeSeason = Season.builder()
                .id(seasonId)
                .competitionId(UUID.randomUUID())
                .currentRoundId(UUID.randomUUID())
                .name("2026/27")
                .slug(SeasonSlug.of("2026-27"))
                .clientId(1)
                .maxRounds(20)
                .totalTeams(12)
                .build();
        Round currentRound = Round.builder()
                .id(activeSeason.getCurrentRoundId())
                .seasonId(seasonId)
                .position(20)
                .name("Round 20")
                .slug("round-20")
                .build();
        when(seasonRepo.findActiveSeason("premier-league")).thenReturn(Optional.of(activeSeason));
        when(roundSupport.resolveCurrentRound(activeSeason)).thenReturn(currentRound);

        // Two private contests on the active tab: one already past its toRound (FINISHED), one
        // currently within its window (LIVE).
        when(contestRepo.countContestsByUserId(userId, seasonId, true)).thenReturn(2);
        when(contestRepo.countContestsByUserId(userId, seasonId, false)).thenReturn(0);
        when(contestRepo.findContestsByUserId(userId, seasonId, true, 10, 0))
                .thenReturn(List.of(view(3, 3), view(15, 20)));
        when(contestRepo.findContestsByUserId(userId, seasonId, false, 10, 0)).thenReturn(List.of());

        when(contestSupport.deriveContestStatus(3, 3, currentRound, CompetitionPhaseFixtures.phases()))
                .thenReturn("FINISHED");
        when(contestSupport.deriveContestStatus(15, 20, currentRound, CompetitionPhaseFixtures.phases()))
                .thenReturn("LIVE");

        var result = useCase.execute(new GetProfileContestListsQuery(userId, 1, 1));

        assertThat(result.activeContests()).hasSize(2);
        assertThat(result.activeContests().get(0).status()).isEqualTo("FINISHED");
        assertThat(result.activeContests().get(1).status()).isEqualTo("LIVE");

        // The current round is resolved once per list build, not once per row.
        verify(roundSupport, times(1)).resolveCurrentRound(activeSeason);
    }

    @Test
    void pastTab_ownedPrivateContest_populatesRenewalFieldsFromRenewalOptionsUseCase() {
        var pastView = view(1, 4, true, true);
        when(contestRepo.countContestsByUserId(userId, null, true)).thenReturn(0);
        when(contestRepo.countContestsByUserId(userId, null, false)).thenReturn(1);
        when(contestRepo.findContestsByUserId(userId, null, true, 10, 0)).thenReturn(List.of());
        when(contestRepo.findContestsByUserId(userId, null, false, 10, 0)).thenReturn(List.of(pastView));

        when(getContestRenewalOptionsUseCase.execute(pastView.contestId(), userId))
                .thenReturn(Either.right(new GetContestRenewalOptionsResult(
                        true, true, "Homeboyz", "S1", "S4", List.of("S1", "S4"), 5)));

        var result = useCase.execute(new GetProfileContestListsQuery(userId, 1, 1));

        var row = result.pastContests().get(0);
        assertThat(row.renewVisible()).isTrue();
        assertThat(row.renewEnabled()).isTrue();
        assertThat(row.renewFromCode()).isEqualTo("S1");
        assertThat(row.renewDefaultToCode()).isEqualTo("S4");
        assertThat(row.renewToOptionCodes()).containsExactly("S1", "S4");
    }

    @Test
    void pastTab_notOwnedContest_neverConsultsRenewalOptionsUseCase() {
        var pastView = view(1, 4, true, false);
        when(contestRepo.countContestsByUserId(userId, null, true)).thenReturn(0);
        when(contestRepo.countContestsByUserId(userId, null, false)).thenReturn(1);
        when(contestRepo.findContestsByUserId(userId, null, true, 10, 0)).thenReturn(List.of());
        when(contestRepo.findContestsByUserId(userId, null, false, 10, 0)).thenReturn(List.of(pastView));

        var result = useCase.execute(new GetProfileContestListsQuery(userId, 1, 1));

        var row = result.pastContests().get(0);
        assertThat(row.renewVisible()).isFalse();
        assertThat(row.renewToOptionCodes()).isEmpty();
        verify(getContestRenewalOptionsUseCase, never()).execute(any(), any());
    }

    @Test
    void activeTab_ownedPrivateContest_neverConsultsRenewalOptionsUseCase() {
        var activeView = view(1, 4, true, true);
        when(contestRepo.countContestsByUserId(userId, null, true)).thenReturn(1);
        when(contestRepo.countContestsByUserId(userId, null, false)).thenReturn(0);
        when(contestRepo.findContestsByUserId(userId, null, true, 10, 0)).thenReturn(List.of(activeView));
        when(contestRepo.findContestsByUserId(userId, null, false, 10, 0)).thenReturn(List.of());

        var result = useCase.execute(new GetProfileContestListsQuery(userId, 1, 1));

        assertThat(result.activeContests().get(0).renewVisible()).isFalse();
        verify(getContestRenewalOptionsUseCase, never()).execute(any(), any());
    }
}
