package com.ligitabl.api.usecase.prediction.latestresult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.ligitabl.api.config.CompetitionDefaults;
import com.ligitabl.model.domain.RoundResult;
import com.ligitabl.model.domain.Season;
import com.ligitabl.model.repo.RoundResultRepo;
import com.ligitabl.model.repo.SeasonRepo;

class ResultBannerSettingUseCasesTest {

    private static final String SLUG = "premier-league";
    private static final UUID USER_ID = UUID.randomUUID();
    private static final UUID SEASON_ID = UUID.randomUUID();

    private final SeasonRepo seasonRepo = mock(SeasonRepo.class);
    private final RoundResultRepo roundResultRepo = mock(RoundResultRepo.class);
    private final CompetitionDefaults defaults = new CompetitionDefaults(SLUG);

    private final GetResultBannerSettingUseCase getSetting =
            new GetResultBannerSettingUseCase(seasonRepo, roundResultRepo, defaults);
    private final RestoreResultBannerUseCase restore =
            new RestoreResultBannerUseCase(seasonRepo, roundResultRepo, defaults);

    @Test
    void noActiveSeasonMeansNoResult() {
        when(seasonRepo.findActiveSeason(SLUG)).thenReturn(Optional.empty());

        assertThat(getSetting.execute(USER_ID)).isEqualTo(new GetResultBannerSettingResult(false, false));
        restore.execute(USER_ID);
        verify(roundResultRepo, never()).save(any());
    }

    @Test
    void undismissedResultIsPending() {
        givenLatestResult(false);

        assertThat(getSetting.execute(USER_ID)).isEqualTo(new GetResultBannerSettingResult(true, true));
    }

    @Test
    void dismissedResultIsPresentButNotPending() {
        givenLatestResult(true);

        assertThat(getSetting.execute(USER_ID)).isEqualTo(new GetResultBannerSettingResult(true, false));
    }

    @Test
    void restoreUndismissesTheLatestResult() {
        var result = givenLatestResult(true);

        restore.execute(USER_ID);

        assertThat(result.isUserViewed()).isFalse();
        verify(roundResultRepo).save(result);
    }

    @Test
    void restoreLeavesAnUndismissedResultAlone() {
        givenLatestResult(false);

        restore.execute(USER_ID);

        verify(roundResultRepo, never()).save(any());
    }

    private RoundResult givenLatestResult(boolean viewed) {
        when(seasonRepo.findActiveSeason(SLUG))
                .thenReturn(Optional.of(Season.builder().id(SEASON_ID).build()));
        var result = RoundResult.builder().build();
        result = result.toBuilder().userViewed(viewed).build();
        when(roundResultRepo.findLatestByUserAndSeason(USER_ID, SEASON_ID)).thenReturn(Optional.of(result));
        return result;
    }
}
