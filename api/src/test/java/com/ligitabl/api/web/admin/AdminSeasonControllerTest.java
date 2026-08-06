package com.ligitabl.api.web.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.ui.ExtendedModelMap;
import org.springframework.ui.Model;

import com.ligitabl.api.testsupport.TestCalendar;
import com.ligitabl.api.config.CompetitionDefaults;
import com.ligitabl.api.rest.season.admin.ActivateSeasonUseCase;
import com.ligitabl.api.rest.season.admin.AssignUpcomingSeasonUseCase;
import com.ligitabl.api.rest.season.admin.RevertSeasonUseCase;
import com.ligitabl.api.rest.season.admin.UpdateSeasonDatesUseCase;
import com.ligitabl.model.domain.Competition;
import com.ligitabl.model.domain.CompetitionSlug;
import com.ligitabl.model.domain.Season;
import com.ligitabl.model.domain.SeasonSlug;
import com.ligitabl.model.repo.CompetitionRepo;
import com.ligitabl.model.repo.SeasonRepo;

/**
 * Covers the model attributes {@code admin/seasons.html} reads, which had no test.
 *
 * <p>Two of them — {@code activeSeasonInPlay} and {@code seasonStatesById} — exist because the
 * template used to call {@code activeSeason.isInPlay()} and {@code s.getSeasonState()} directly.
 * Those predicates now require an explicit instant, so the values are computed here instead. The
 * failure modes are asymmetric and both bad: a missing {@code seasonStatesById} throws at render
 * time, while a missing {@code activeSeasonInPlay} just makes the "(opened)" badge quietly vanish.
 * Neither would have been caught by anything before this class existed.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("AdminSeasonController — seasons page model")
class AdminSeasonControllerTest {

    /** Mid-season, derived — see {@link TestCalendar}. The absolute date is not load-bearing here. */
    private static final Instant NOW = TestCalendar.middayMidwayBetween(
            LocalDate.of(TestCalendar.SEASON_START_YEAR, 8, 1), LocalDate.of(TestCalendar.SEASON_START_YEAR + 1, 5, 31));
    private static final LocalDate TODAY = LocalDate.ofInstant(NOW, ZoneOffset.UTC);
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

    @Mock
    private CompetitionRepo competitionRepo;

    @Mock
    private SeasonRepo seasonRepo;

    @Mock
    private ActivateSeasonUseCase activateSeasonUseCase;

    @Mock
    private RevertSeasonUseCase revertSeasonUseCase;

    @Mock
    private AssignUpcomingSeasonUseCase assignUpcomingSeasonUseCase;

    @Mock
    private UpdateSeasonDatesUseCase updateSeasonDatesUseCase;

    private AdminSeasonController controller;
    private final Model model = new ExtendedModelMap();

    private final UUID competitionId = UUID.randomUUID();
    private final UUID activeSeasonId = UUID.randomUUID();
    private final UUID upcomingSeasonId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        controller = new AdminSeasonController(
                new CompetitionDefaults("premier-league"),
                competitionRepo,
                seasonRepo,
                activateSeasonUseCase,
                revertSeasonUseCase,
                assignUpcomingSeasonUseCase,
                updateSeasonDatesUseCase,
                CLOCK);
    }

    @Test
    void exposesTheStateOfEveryRowInTheSeasonsTable() {
        Season active = inPlaySeason(activeSeasonId);
        Season upcoming = preSeasonSeason(upcomingSeasonId);
        givenCompetition(active, upcoming, List.of(active, upcoming));

        controller.seasonsPage(model);

        @SuppressWarnings("unchecked")
        Map<UUID, String> states = (Map<UUID, String>) model.getAttribute("seasonStatesById");
        assertThat(states)
                .as("every row in allSeasons needs a state, or the template renders a blank cell")
                .containsOnlyKeys(activeSeasonId, upcomingSeasonId)
                .containsEntry(activeSeasonId, "IN_PLAY")
                .containsEntry(upcomingSeasonId, "PRE_SEASON");
    }

    @Test
    void exposesWhetherTheActiveSeasonIsInPlay_forThePredictionsOpenedBadge() {
        Season active = inPlaySeason(activeSeasonId);
        givenCompetition(active, null, List.of(active));

        controller.seasonsPage(model);

        assertThat(model.getAttribute("activeSeasonInPlay")).isEqualTo(true);
        assertThat(model.getAttribute("activeSeasonState")).isEqualTo("IN_PLAY");
    }

    @Test
    void theBadgeIsOffWhilePredictionsHaveNotOpened() {
        Season active = preSeasonSeason(activeSeasonId);
        givenCompetition(active, null, List.of(active));

        controller.seasonsPage(model);

        assertThat(model.getAttribute("activeSeasonInPlay"))
                .as("false, not absent — an absent attribute hides the badge for the wrong reason")
                .isEqualTo(false);
    }

    /**
     * The countdowns and the phase states are two views of the same moment, so they have to be read
     * from one instant. Before this, each was computed from its own {@code now()} call.
     */
    @Test
    void countdownsAreMeasuredFromTheSameInstantAsTheStates() {
        Season active = inPlaySeason(activeSeasonId);
        active.setPreSeasonOpensAt(OffsetDateTime.ofInstant(NOW, ZoneOffset.UTC).plusDays(10));
        Season upcoming = preSeasonSeason(upcomingSeasonId);
        upcoming.setPredictionsOpenAt(OffsetDateTime.ofInstant(NOW, ZoneOffset.UTC).plusDays(4));
        givenCompetition(active, upcoming, List.of(active, upcoming));

        controller.seasonsPage(model);

        assertThat(model.getAttribute("now")).isEqualTo(OffsetDateTime.ofInstant(NOW, ZoneOffset.UTC));
        assertThat(model.getAttribute("daysToPreSeason")).isEqualTo(10L);
        assertThat(model.getAttribute("daysToPredictions")).isEqualTo(4L);
    }

    @Test
    void noCompetition_returnsTheViewWithoutTouchingTheClock() {
        when(competitionRepo.findBySlug("premier-league")).thenReturn(Optional.empty());

        assertThat(controller.seasonsPage(model)).isEqualTo("admin/seasons");
        assertThat(model.getAttribute("seasonStatesById")).isNull();
    }

    private void givenCompetition(Season active, Season upcoming, List<Season> all) {
        Competition competition = Competition.builder()
                .name("Premier League")
                .slug(CompetitionSlug.of("premier-league"))
                .code("PL")
                .activeSeasonId(active != null ? active.getId() : null)
                .upcomingSeasonId(upcoming != null ? upcoming.getId() : null)
                .build();
        competition.setId(competitionId);

        when(competitionRepo.findBySlug("premier-league")).thenReturn(Optional.of(competition));
        when(seasonRepo.findAllByCompetitionId(competitionId)).thenReturn(all);
        all.forEach(s -> when(seasonRepo.findById(s.getId())).thenReturn(Optional.of(s)));
    }

    /** Predictions already open, season under way. */
    private Season inPlaySeason(UUID id) {
        Season season = season(id, TODAY.minusMonths(1), TODAY.plusMonths(8));
        season.setPreSeasonOpensAt(OffsetDateTime.ofInstant(NOW, ZoneOffset.UTC).minusDays(30));
        season.setPredictionsOpenAt(OffsetDateTime.ofInstant(NOW, ZoneOffset.UTC).minusDays(7));
        return season;
    }

    /** Pre-season opened, predictions not yet, first kickoff still ahead. */
    private Season preSeasonSeason(UUID id) {
        Season season = season(id, TODAY.plusMonths(1), TODAY.plusMonths(10));
        season.setPreSeasonOpensAt(OffsetDateTime.ofInstant(NOW, ZoneOffset.UTC).minusDays(3));
        season.setPredictionsOpenAt(OffsetDateTime.ofInstant(NOW, ZoneOffset.UTC).plusDays(20));
        return season;
    }

    private Season season(UUID id, LocalDate start, LocalDate end) {
        Season season = Season.builder()
                .clientId(1)
                .competitionId(competitionId)
                .name("Test Season")
                .slug(SeasonSlug.of("2026-27"))
                .startDate(start)
                .endDate(end)
                .completed(false)
                .build();
        season.setId(id);
        return season;
    }
}
