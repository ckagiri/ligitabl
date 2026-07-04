package com.ligitabl.api.rest.season.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.ligitabl.model.domain.Competition;
import com.ligitabl.model.domain.CompetitionSlug;
import com.ligitabl.model.domain.Season;
import com.ligitabl.model.domain.SeasonSlug;
import com.ligitabl.model.repo.CompetitionRepo;
import com.ligitabl.model.repo.SeasonRepo;

@ExtendWith(MockitoExtension.class)
class UpdateSeasonDatesUseCaseTest {

    @Mock
    SeasonRepo seasonRepo;

    @Mock
    CompetitionRepo competitionRepo;

    private UpdateSeasonDatesUseCase useCase;
    private UUID competitionId;
    private UUID outgoingId;
    private UUID upcomingId;

    @BeforeEach
    void setUp() {
        useCase = new UpdateSeasonDatesUseCase(seasonRepo, competitionRepo);
        competitionId = UUID.randomUUID();
        outgoingId = UUID.randomUUID();
        upcomingId = UUID.randomUUID();
    }

    @Test
    void happyPath_updatesBothSeasons_noCompetitionPropagation() {
        Season outgoing = inPlaySeason(outgoingId);
        Season upcoming = upcomingCandidateSeason(upcomingId);
        OffsetDateTime preSeasonOpensAt = OffsetDateTime.now().plusDays(10);
        OffsetDateTime predictionsOpenAt = OffsetDateTime.now().plusDays(20);

        when(seasonRepo.findById(outgoingId)).thenReturn(Optional.of(outgoing));
        when(seasonRepo.findById(upcomingId)).thenReturn(Optional.of(upcoming));
        when(competitionRepo.findById(competitionId)).thenReturn(Optional.of(competition(upcomingId)));

        var result = useCase.execute(outgoingId, preSeasonOpensAt, upcomingId, predictionsOpenAt);

        assertThat(result).isInstanceOf(UpdateSeasonDatesUseCase.Result.Ok.class);
        assertThat(outgoing.getPreSeasonOpensAt()).isEqualTo(preSeasonOpensAt);
        assertThat(upcoming.getPredictionsOpenAt()).isEqualTo(predictionsOpenAt);
        // upcomingId already matches the competition's real upcoming season, so preSeasonOpensAt
        // propagation lands on the same `upcoming` object rather than a separate save.
        assertThat(upcoming.getPreSeasonOpensAt()).isEqualTo(preSeasonOpensAt);
        verify(seasonRepo).save(outgoing);
        verify(seasonRepo).save(upcoming);
        verify(seasonRepo, times(2)).save(any());
    }

    @Test
    void outgoingAllowedToBecomeInactive_savesWithoutRejection() {
        // The outgoing season is on its way out regardless — landing on INACTIVE is fine and
        // should not block the update, unlike the same combination on an incoming season.
        Season outgoing = inactiveSeason(outgoingId);

        when(seasonRepo.findById(outgoingId)).thenReturn(Optional.of(outgoing));
        when(competitionRepo.findById(competitionId)).thenReturn(Optional.of(competition(null)));

        var result = useCase.execute(outgoingId, null, null, null);

        assertThat(result).isInstanceOf(UpdateSeasonDatesUseCase.Result.Ok.class);
        verify(seasonRepo).save(outgoing);
    }

    @Test
    void upcomingWouldBeInactive_rejectsWithoutSaving() {
        Season upcoming = inactiveSeason(upcomingId);

        when(seasonRepo.findById(upcomingId)).thenReturn(Optional.of(upcoming));

        // Must pass a real future date here — passing null would overwrite the fixture's
        // pre-set predictionsOpenAt back to null (open), masking the INACTIVE combo.
        var result = useCase.execute(null, null, upcomingId, OffsetDateTime.now().plusDays(5));

        assertThat(result).isInstanceOf(UpdateSeasonDatesUseCase.Result.InvalidDateOrder.class);
        verify(seasonRepo, never()).save(any());
        verifyNoInteractions(competitionRepo);
    }

    @Test
    void propagatesPreSeasonOpensAt_ontoCompetitionsRealUpcomingSeason_whenRequestOmitsIt() {
        Season outgoing = inPlaySeason(outgoingId);
        UUID realUpcomingId = UUID.randomUUID();
        Season realUpcoming = inPlaySeason(realUpcomingId);
        OffsetDateTime preSeasonOpensAt = OffsetDateTime.now().plusDays(10);

        when(seasonRepo.findById(outgoingId)).thenReturn(Optional.of(outgoing));
        when(competitionRepo.findById(competitionId)).thenReturn(Optional.of(competition(realUpcomingId)));
        when(seasonRepo.findById(realUpcomingId)).thenReturn(Optional.of(realUpcoming));

        // Request doesn't reference an upcomingSeasonId at all.
        var result = useCase.execute(outgoingId, preSeasonOpensAt, null, null);

        assertThat(result).isInstanceOf(UpdateSeasonDatesUseCase.Result.Ok.class);
        assertThat(realUpcoming.getPreSeasonOpensAt()).isEqualTo(preSeasonOpensAt);
        verify(seasonRepo).save(outgoing);
        verify(seasonRepo).save(realUpcoming);
    }

    @Test
    void propagatesPreSeasonOpensAt_whenRequestsUpcomingIdDiffersFromCompetitions() {
        Season outgoing = inPlaySeason(outgoingId);
        Season requestUpcoming = upcomingCandidateSeason(upcomingId);
        UUID realUpcomingId = UUID.randomUUID();
        Season realUpcoming = upcomingCandidateSeason(realUpcomingId);
        OffsetDateTime preSeasonOpensAt = OffsetDateTime.now().plusDays(10);
        OffsetDateTime predictionsOpenAt = OffsetDateTime.now().plusDays(20);

        when(seasonRepo.findById(outgoingId)).thenReturn(Optional.of(outgoing));
        when(seasonRepo.findById(upcomingId)).thenReturn(Optional.of(requestUpcoming));
        when(competitionRepo.findById(competitionId)).thenReturn(Optional.of(competition(realUpcomingId)));
        when(seasonRepo.findById(realUpcomingId)).thenReturn(Optional.of(realUpcoming));

        var result = useCase.execute(outgoingId, preSeasonOpensAt, upcomingId, predictionsOpenAt);

        assertThat(result).isInstanceOf(UpdateSeasonDatesUseCase.Result.Ok.class);
        assertThat(requestUpcoming.getPredictionsOpenAt()).isEqualTo(predictionsOpenAt);
        assertThat(requestUpcoming.getPreSeasonOpensAt()).isNull();
        assertThat(realUpcoming.getPreSeasonOpensAt()).isEqualTo(preSeasonOpensAt);
        verify(seasonRepo).save(outgoing);
        verify(seasonRepo).save(requestUpcoming);
        verify(seasonRepo).save(realUpcoming);
    }

    @Test
    void noCompetitionUpcomingSeason_noPropagation() {
        Season outgoing = inPlaySeason(outgoingId);
        OffsetDateTime preSeasonOpensAt = OffsetDateTime.now().plusDays(10);

        when(seasonRepo.findById(outgoingId)).thenReturn(Optional.of(outgoing));
        when(competitionRepo.findById(competitionId)).thenReturn(Optional.of(competition(null)));

        var result = useCase.execute(outgoingId, preSeasonOpensAt, null, null);

        assertThat(result).isInstanceOf(UpdateSeasonDatesUseCase.Result.Ok.class);
        verify(seasonRepo).save(outgoing);
        verify(seasonRepo, times(1)).save(any());
    }

    private Competition competition(UUID upcomingSeasonId) {
        return Competition.builder()
                .id(competitionId)
                .name("Premier League")
                .slug(CompetitionSlug.of("premier-league"))
                .code("PL")
                .upcomingSeasonId(upcomingSeasonId)
                .build();
    }

    /** IN_PLAY: not completed, started, predictionsOpenAt open (null defaults open). */
    private Season inPlaySeason(UUID id) {
        return Season.builder()
                .id(id)
                .clientId(1)
                .competitionId(competitionId)
                .name("2026/27")
                .slug(SeasonSlug.of("2026-27"))
                .startDate(LocalDate.now().minusMonths(1))
                .endDate(LocalDate.now().plusMonths(9))
                .completed(false)
                .initialRankings(java.util.List.of())
                .build();
    }

    /**
     * A season that hasn't started yet (future startDate) — used for "upcoming" fixtures so that
     * setting a future preSeasonOpensAt/predictionsOpenAt on them lands on OFF_SEASON/IN_PLAY
     * rather than the INACTIVE limbo state a *started* season would fall into under the same
     * combination (see Season.getSeasonState()'s beforeActualStart/pastActualEnd guards).
     */
    private Season upcomingCandidateSeason(UUID id) {
        return Season.builder()
                .id(id)
                .clientId(1)
                .competitionId(competitionId)
                .name("2027/28")
                .slug(SeasonSlug.of("2027-28"))
                .startDate(LocalDate.now().plusMonths(1))
                .endDate(LocalDate.now().plusMonths(10))
                .completed(false)
                .initialRankings(java.util.List.of())
                .build();
    }

    /**
     * INACTIVE: already started (not beforeActualStart), predictions still gated in the future
     * (not open), but preSeasonOpensAt already in the past (open) — none of OFF_SEASON/IN_PLAY/
     * PRE_SEASON apply, so Season.getSeasonState() falls through to INACTIVE.
     */
    private Season inactiveSeason(UUID id) {
        return Season.builder()
                .id(id)
                .clientId(1)
                .competitionId(competitionId)
                .name("2026/27")
                .slug(SeasonSlug.of("2026-27"))
                .startDate(LocalDate.now().minusMonths(1))
                .endDate(LocalDate.now().plusMonths(9))
                .completed(false)
                .preSeasonOpensAt(OffsetDateTime.now().minusDays(5))
                .predictionsOpenAt(OffsetDateTime.now().plusDays(5))
                .initialRankings(java.util.List.of())
                .build();
    }
}
