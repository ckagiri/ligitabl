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

        if (outgoingSeasonId != null) {
            outgoing = seasonRepo.findById(outgoingSeasonId).orElse(null);
            if (outgoing == null) return new Result.OutgoingSeasonNotFound(outgoingSeasonId);
            outgoing.setPreSeasonOpensAt(preSeasonOpensAt);

            // Propagate the outgoing season's preSeasonOpensAt onto the competition's actual
            // upcoming season, which may differ from (or be absent vs.) upcomingSeasonId above —
            // the idea being that the outgoing season's pre-season-opens date becomes the
            // incoming season's pre-season-opens date too, ready for when it becomes outgoing.
            var competition = competitionRepo.findById(outgoing.getCompetitionId()).orElse(null);
            if (competition != null && competition.getUpcomingSeasonId() != null) {
                if (competition.getUpcomingSeasonId().equals(upcomingSeasonId)) {
                    // Same season the request already targets below — avoid a duplicate fetch/save.
                } else {
                    competitionUpcoming = seasonRepo
                            .findById(competition.getUpcomingSeasonId())
                            .orElse(null);
                    if (competitionUpcoming != null) {
                        competitionUpcoming.setPreSeasonOpensAt(preSeasonOpensAt);
                    }
                }
            }
        }

        if (upcomingSeasonId != null) {
            upcoming = seasonRepo.findById(upcomingSeasonId).orElse(null);
            if (upcoming == null) return new Result.UpcomingSeasonNotFound(upcomingSeasonId);
            upcoming.setPredictionsOpenAt(predictionsOpenAt);
            if (competitionUpcoming == null && outgoing != null) {
                // The request's own upcomingSeasonId matched the competition's upcoming season —
                // apply the preSeasonOpensAt propagation to this same object.
                var competition =
                        competitionRepo.findById(outgoing.getCompetitionId()).orElse(null);
                if (competition != null && upcomingSeasonId.equals(competition.getUpcomingSeasonId())) {
                    upcoming.setPreSeasonOpensAt(preSeasonOpensAt);
                }
            }
        }

        // Reject if either touched season would land on INACTIVE, before persisting anything.
        if (outgoing != null && outgoing.getSeasonState() == SeasonState.INACTIVE) {
            return new Result.InvalidDateOrder(
                    "Resulting state for outgoing season " + outgoingSeasonId + " would be INACTIVE");
        }
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
        if (upcoming != null) {
            seasonRepo.save(upcoming);
            log.info("[ADMIN_SEASON_DATES] upcoming={} predictionsOpenAt={}", upcomingSeasonId, predictionsOpenAt);
        }

        return new Result.Ok();
    }
}
