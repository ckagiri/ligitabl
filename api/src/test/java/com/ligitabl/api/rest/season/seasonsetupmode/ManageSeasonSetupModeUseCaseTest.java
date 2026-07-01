package com.ligitabl.api.rest.season.seasonsetupmode;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import com.ligitabl.api.config.CompetitionDefaults;
import com.ligitabl.api.rest.shared.HierarchyValidator;
import com.ligitabl.api.shared.Either;
import com.ligitabl.model.domain.Season;
import com.ligitabl.model.domain.SeasonSlug;
import com.ligitabl.model.repo.SeasonRepo;

class ManageSeasonSetupModeUseCaseTest {

    @Mock
    SeasonRepo seasonRepo;

    @Mock
    HierarchyValidator hierarchyValidator;

    private final CompetitionDefaults competitionDefaults = new CompetitionDefaults("premier-league");

    private final Clock clock = Clock.fixed(Instant.parse("2026-07-01T12:00:00Z"), ZoneOffset.UTC);

    ManageSeasonSetupModeUseCase useCase;

    @BeforeEach
    void setup() {
        MockitoAnnotations.openMocks(this);
        useCase = new ManageSeasonSetupModeUseCase(seasonRepo, hierarchyValidator, competitionDefaults, clock);
    }

    private Season.SeasonBuilder<?, ?> baseSeason(UUID seasonId, UUID mainContestId, UUID detachedContestId) {
        return Season.builder()
                .id(seasonId)
                .slug(SeasonSlug.of("2025-26"))
                .mainContestId(mainContestId)
                .detachedContestId(detachedContestId);
    }

    @Test
    void enter_setup_mode_succeeds_even_with_existing_round_submissions() {
        UUID seasonId = UUID.randomUUID();
        UUID mainContestId = UUID.randomUUID();
        Season season = baseSeason(seasonId, mainContestId, null).build();

        when(hierarchyValidator.validateCompetitionAndSeason(any(), any())).thenReturn(Either.right(season));
        when(seasonRepo.save(any(Season.class))).thenAnswer(inv -> inv.getArgument(0));

        var cmd = SeasonSetupModeCommand.builder()
                .seasonSlug("2025-26")
                .action(SetupModeAction.ENTER)
                .build();

        var result = useCase.execute(cmd);

        assertThat(result.isRight()).isTrue();
        assertThat(result.get().isInSetupMode()).isTrue();
        assertThat(season.getMainContestId()).isNull();
        assertThat(season.getDetachedContestId()).isEqualTo(mainContestId);
        verify(seasonRepo).save(season);
    }

    @Test
    void enter_setup_mode_fails_when_already_in_setup_mode() {
        UUID seasonId = UUID.randomUUID();
        UUID detachedContestId = UUID.randomUUID();
        Season season = baseSeason(seasonId, null, detachedContestId).build();

        when(hierarchyValidator.validateCompetitionAndSeason(any(), any())).thenReturn(Either.right(season));

        var cmd = SeasonSetupModeCommand.builder()
                .seasonSlug("2025-26")
                .action(SetupModeAction.ENTER)
                .build();

        var result = useCase.execute(cmd);

        assertThat(result.isLeft()).isTrue();
        assertThat(result.getLeft().getMessage()).contains("already in setup mode");
        verify(seasonRepo, never()).save(any());
    }

    @Test
    void leave_setup_mode_reattaches_main_contest() {
        UUID seasonId = UUID.randomUUID();
        UUID detachedContestId = UUID.randomUUID();
        Season season = baseSeason(seasonId, null, detachedContestId).build();

        when(hierarchyValidator.validateCompetitionAndSeason(any(), any())).thenReturn(Either.right(season));
        when(seasonRepo.save(any(Season.class))).thenAnswer(inv -> inv.getArgument(0));

        var cmd = SeasonSetupModeCommand.builder()
                .seasonSlug("2025-26")
                .action(SetupModeAction.LEAVE)
                .build();

        var result = useCase.execute(cmd);

        assertThat(result.isRight()).isTrue();
        assertThat(result.get().isInSetupMode()).isFalse();
        assertThat(season.getMainContestId()).isEqualTo(detachedContestId);
        assertThat(season.getDetachedContestId()).isNull();
    }

    @Test
    void leave_setup_mode_fails_when_not_in_setup_mode() {
        UUID seasonId = UUID.randomUUID();
        UUID mainContestId = UUID.randomUUID();
        Season season = baseSeason(seasonId, mainContestId, null).build();

        when(hierarchyValidator.validateCompetitionAndSeason(any(), any())).thenReturn(Either.right(season));

        var cmd = SeasonSetupModeCommand.builder()
                .seasonSlug("2025-26")
                .action(SetupModeAction.LEAVE)
                .build();

        var result = useCase.execute(cmd);

        assertThat(result.isLeft()).isTrue();
        assertThat(result.getLeft().getMessage()).contains("not in setup mode");
        verify(seasonRepo, never()).save(any());
    }
}
