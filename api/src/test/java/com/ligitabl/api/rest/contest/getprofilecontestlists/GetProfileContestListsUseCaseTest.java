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
import com.ligitabl.api.rest.contest.shared.ContestRankResolver;
import com.ligitabl.api.rest.round.shared.RoundSupport;
import com.ligitabl.api.web.contest.shared.ContestSupport;
import com.ligitabl.model.domain.Competition;
import com.ligitabl.model.domain.CompetitionSlug;
import com.ligitabl.model.domain.PhaseType;
import com.ligitabl.model.domain.Round;
import com.ligitabl.model.domain.RoundSpan;
import com.ligitabl.model.domain.Season;
import com.ligitabl.model.domain.SeasonSlug;
import com.ligitabl.model.repo.CompetitionRepo;
import com.ligitabl.model.repo.ContestRepo;
import com.ligitabl.model.repo.EntryRepo;
import com.ligitabl.model.repo.SeasonRepo;

/**
 * Phases mirror a season's S1-S8 / Q1-Q4 structure, one round per sprint: S1=round1 ...
 * S8=round8, Q1=S1+S2, Q2=S3+S4, Q3=S5+S6, Q4=S7+S8.
 */
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
                roundSupport);

        userId = UUID.randomUUID();
        seasonId = UUID.randomUUID();

        Competition competition = Competition.builder()
                .id(UUID.randomUUID())
                .name("Premier League")
                .slug(CompetitionSlug.of("premier-league"))
                .code("PL")
                .phases(buildPhases())
                .build();
        when(competitionRepo.findBySlug("premier-league")).thenReturn(Optional.of(competition));
        when(seasonRepo.findActiveSeason("premier-league")).thenReturn(Optional.empty());
        when(entryRepo.countActiveByContestIds(any())).thenReturn(Map.of());
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
        List<RoundSpan> quarters =
                List.of(quarter("Q1", 1, 2), quarter("Q2", 3, 4), quarter("Q3", 5, 6), quarter("Q4", 7, 8));
        return java.util.stream.Stream.concat(sprints.stream(), quarters.stream())
                .toList();
    }

    private static RoundSpan sprint(String code, int from, int to) {
        return RoundSpan.builder()
                .code(code)
                .name(code)
                .type(PhaseType.SPRINT)
                .from(from)
                .to(to)
                .build();
    }

    private static RoundSpan quarter(String code, int from, int to) {
        return RoundSpan.builder()
                .code(code)
                .name(code)
                .type(PhaseType.QUARTER)
                .from(from)
                .to(to)
                .build();
    }

    private ContestRepo.UserContestView view(int from, int to) {
        return new ContestRepo.UserContestView(
                UUID.randomUUID(), "Homeboyz", seasonId, "2026/27", false, from, to, true, true, false);
    }

    @Test
    void periodLabel_multiSprintWindow_appearsInSummary() {
        when(contestRepo.countContestsByUserId(userId, null, true)).thenReturn(1);
        when(contestRepo.countContestsByUserId(userId, null, false)).thenReturn(0);
        when(contestRepo.findContestsByUserId(userId, null, true, 10, 0)).thenReturn(List.of(view(3, 6))); // Q2-3
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
        when(contestRepo.findContestsByUserId(userId, null, true, 10, 0)).thenReturn(List.of(view(3, 3))); // S3
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

        when(contestSupport.deriveContestStatus(3, 3, currentRound, buildPhases())).thenReturn("FINISHED");
        when(contestSupport.deriveContestStatus(15, 20, currentRound, buildPhases())).thenReturn("LIVE");

        var result = useCase.execute(new GetProfileContestListsQuery(userId, 1, 1));

        assertThat(result.activeContests()).hasSize(2);
        assertThat(result.activeContests().get(0).status()).isEqualTo("FINISHED");
        assertThat(result.activeContests().get(1).status()).isEqualTo("LIVE");

        // The current round is resolved once per list build, not once per row.
        verify(roundSupport, times(1)).resolveCurrentRound(activeSeason);
    }
}
