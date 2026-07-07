package com.ligitabl.api.rest.season.admin;

import java.time.OffsetDateTime;
import java.util.UUID;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.ligitabl.model.domain.Season;
import com.ligitabl.model.domain.SeasonState;
import com.ligitabl.model.repo.CompetitionRepo;
import com.ligitabl.model.repo.SeasonRepo;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Component
@RequiredArgsConstructor
@Slf4j
public class UpdateSeasonDatesUseCase {

    private final SeasonRepo seasonRepo;
    private final CompetitionRepo competitionRepo;

    public sealed interface Result
            permits Result.Ok, Result.OutgoingSeasonNotFound, Result.UpcomingSeasonNotFound, Result.InvalidDateOrder {
        record Ok() implements Result {}

        record OutgoingSeasonNotFound(UUID seasonId) implements Result {}

        record UpcomingSeasonNotFound(UUID seasonId) implements Result {}

        record InvalidDateOrder(String message) implements Result {}
    }

    @Transactional
    public Result execute(
            UUID outgoingSeasonId,
            OffsetDateTime preSeasonOpensAt,
            UUID upcomingSeasonId,
            OffsetDateTime predictionsOpenAt) {

        if (preSeasonOpensAt != null && predictionsOpenAt != null && !preSeasonOpensAt.isBefore(predictionsOpenAt)) {
            return new Result.InvalidDateOrder("preSeasonOpensAt must be before predictionsOpenAt");
        }

        Season outgoing = null;
        Season upcoming = null;
        Season competitionUpcoming = null;
        // When there's no real upcoming season, the admin UI targets predictionsOpenAt at the
        // active/outgoing season itself, so outgoingSeasonId and upcomingSeasonId arrive equal.
        boolean sameSeason = outgoingSeasonId != null && outgoingSeasonId.equals(upcomingSeasonId);

        if (outgoingSeasonId != null) {
            outgoing = seasonRepo.findById(outgoingSeasonId).orElse(null);
            if (outgoing == null) return new Result.OutgoingSeasonNotFound(outgoingSeasonId);
            outgoing.setPreSeasonOpensAt(preSeasonOpensAt);
        }

        if (upcomingSeasonId != null) {
            Season resolvedUpcoming = sameSeason
                    ? outgoing
                    : seasonRepo.findById(upcomingSeasonId).orElse(null);
            if (resolvedUpcoming == null) return new Result.UpcomingSeasonNotFound(upcomingSeasonId);
            resolvedUpcoming.setPredictionsOpenAt(predictionsOpenAt);
            upcoming = resolvedUpcoming;
        }

        // Propagate the outgoing season's preSeasonOpensAt onto the competition's actual upcoming
        // season, which may differ from (or be absent vs.) the request's own upcomingSeasonId —
        // the idea being that the outgoing season's pre-season-opens date becomes the incoming
        // season's pre-season-opens date too, ready for when it becomes outgoing in turn.
        if (outgoing != null) {
            var competition =
                    competitionRepo.findById(outgoing.getCompetitionId()).orElse(null);
            UUID competitionUpcomingId = competition != null ? competition.getUpcomingSeasonId() : null;
            if (competitionUpcomingId != null) {
                if (upcoming != null && competitionUpcomingId.equals(upcomingSeasonId)) {
                    upcoming.setPreSeasonOpensAt(preSeasonOpensAt);
                } else {
                    competitionUpcoming =
                            seasonRepo.findById(competitionUpcomingId).orElse(null);
                    if (competitionUpcoming != null) {
                        competitionUpcoming.setPreSeasonOpensAt(preSeasonOpensAt);
                    }
                }
            }
        }

        // Reject if either incoming season would land on INACTIVE, before persisting anything.
        // The outgoing season is allowed to become INACTIVE — it's on its way out regardless.
        if (upcoming != null && upcoming.getSeasonState() == SeasonState.INACTIVE) {
            return new Result.InvalidDateOrder(
                    "Resulting state for upcoming season " + upcomingSeasonId + " would be INACTIVE");
        }
        if (competitionUpcoming != null && competitionUpcoming.getSeasonState() == SeasonState.INACTIVE) {
            return new Result.InvalidDateOrder("Resulting state for competition's upcoming season "
                    + competitionUpcoming.getId() + " would be INACTIVE");
        }

        if (outgoing != null) {
            seasonRepo.save(outgoing);
            log.info("[ADMIN_SEASON_DATES] outgoing={} preSeasonOpensAt={}", outgoingSeasonId, preSeasonOpensAt);
        }
        if (competitionUpcoming != null) {
            seasonRepo.save(competitionUpcoming);
            log.info(
                    "[ADMIN_SEASON_DATES] competition-upcoming={} preSeasonOpensAt={} (propagated)",
                    competitionUpcoming.getId(),
                    preSeasonOpensAt);
        }
        if (upcoming != null && !sameSeason) {
            seasonRepo.save(upcoming);
            log.info("[ADMIN_SEASON_DATES] upcoming={} predictionsOpenAt={}", upcomingSeasonId, predictionsOpenAt);
        } else if (upcoming != null) {
            log.info(
                    "[ADMIN_SEASON_DATES] predictionsOpenAt={} applied to active season {} (no upcoming season)",
                    predictionsOpenAt,
                    upcomingSeasonId);
        }

        return new Result.Ok();
    }
}
