package com.ligitabl.api.web.rounds;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import com.ligitabl.api.config.CompetitionDefaults;
import com.ligitabl.api.shared.Either;
import com.ligitabl.api.usecase.round.advanceround.AdvanceCurrentRoundNowUseCase;
import com.ligitabl.api.usecase.round.advanceround.CancelRoundAdvancementUseCase;
import com.ligitabl.api.usecase.round.finalizeround.FinalizeRoundCommand;
import com.ligitabl.api.usecase.round.finalizeround.FinalizeRoundError;
import com.ligitabl.api.usecase.round.finalizeround.FinalizeRoundResult;
import com.ligitabl.api.usecase.round.finalizeround.FinalizeRoundUseCase;
import com.ligitabl.model.domain.league.Season;
import com.ligitabl.model.repo.SeasonRepo;

@ExtendWith(MockitoExtension.class)
class FinalizeAndAdvanceRoundControllerTest {

    @Mock
    private SeasonRepo seasonRepo;

    @Mock
    private FinalizeRoundUseCase finalizeRoundUseCase;

    @Mock
    private AdvanceCurrentRoundNowUseCase advanceCurrentRoundNowUseCase;

    @Mock
    private CancelRoundAdvancementUseCase cancelRoundAdvancementUseCase;

    private FinalizeAndAdvanceRoundController controller;

    @BeforeEach
    void setUp() {
        controller = new FinalizeAndAdvanceRoundController(
                seasonRepo,
                new CompetitionDefaults("premier-league"),
                finalizeRoundUseCase,
                advanceCurrentRoundNowUseCase,
                cancelRoundAdvancementUseCase);

        Season season = mock(Season.class);
        when(season.getId()).thenReturn(UUID.randomUUID());
        when(seasonRepo.findActiveSeason(anyString())).thenReturn(Optional.of(season));
    }

    @Test
    void anExpectedFinalizeFailureKeepsItsReasonBelowA500() {
        when(finalizeRoundUseCase.execute(any(FinalizeRoundCommand.class)))
                .thenReturn(Either.left(new FinalizeRoundError.AlreadyFinalized(UUID.randomUUID())));

        var response = controller.finalizeRound();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody()).isEqualTo("Round is already finalized");
    }

    @Test
    void anAdvanceFailureAfterFinalizingStillRedirects() {
        when(finalizeRoundUseCase.execute(any(FinalizeRoundCommand.class)))
                .thenReturn(Either.right(new FinalizeRoundResult(UUID.randomUUID(), 1, 0, 0, false, Instant.now())));
        when(advanceCurrentRoundNowUseCase.execute()).thenThrow(new IllegalStateException("save failed"));

        var response = controller.finalizeAndAdvance();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getHeaders().getFirst("HX-Redirect")).isEqualTo("/rounds/current/matches");
    }
}
