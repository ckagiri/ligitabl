package com.ligitabl.api.usecase.season.switchseason;

import java.time.Clock;
import java.util.UUID;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.ligitabl.api.usecase.shared.AdminAlerts;
import com.ligitabl.model.domain.Competition;
import com.ligitabl.model.domain.Season;
import com.ligitabl.model.repo.CompetitionRepo;
import com.ligitabl.model.repo.SeasonRepo;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Promotes the competition's upcoming season once the active one is completed and its pre-season
 * window has opened. Requires an upcoming season already assigned via admin — does not derive one.
 * Idempotent: after a switch the new active season is neither completed nor pre-season open.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class AutoActivateSeasonUseCase {

    private final CompetitionRepo competitionRepo;
    private final SeasonRepo seasonRepo;
    private final ActivateSeasonUseCase activateSeasonUseCase;
    private final AdminAlerts adminAlerts;
    private final Clock clock;

    @Transactional
    public AutoActivateSeasonResult execute(String competitionSlug) {
        Competition competition = competitionRepo.findBySlug(competitionSlug).orElse(null);
        if (competition == null) {
            return new AutoActivateSeasonResult.CompetitionNotFound(competitionSlug);
        }

        UUID activeSeasonId = competition.getActiveSeasonId();
        Season activeSeason = activeSeasonId == null
                ? null
                : seasonRepo.findById(activeSeasonId).orElse(null);
        if (activeSeason == null || !activeSeason.isCompleted() || !activeSeason.isPreSeasonOpen(clock.instant())) {
            return new AutoActivateSeasonResult.NotDue();
        }

        log.info(
                "[SEASON_ACTIVATION] Promoting competition {} upcomingSeasonId {} to activeSeasonId (was {})",
                competitionSlug,
                competition.getUpcomingSeasonId(),
                activeSeasonId);
        return switch (activateSeasonUseCase.execute(competitionSlug, null)) {
            case ActivateSeasonResult.Ok ok -> {
                adminAlerts.notifySeasonActivated(competitionSlug, ok.newActiveSeasonId(), activeSeasonId);
                yield new AutoActivateSeasonResult.Activated(ok.newActiveSeasonId(), activeSeasonId);
            }
            case ActivateSeasonResult.NoUpcomingSeason ignored -> {
                log.debug(
                        "[SEASON_ACTIVATION] No upcoming season assigned for competition {}, no switch",
                        competitionSlug);
                yield new AutoActivateSeasonResult.NoUpcomingSeason();
            }
            case ActivateSeasonResult.CompetitionNotFound ignored -> new AutoActivateSeasonResult.CompetitionNotFound(
                    competitionSlug);
        };
    }
}
