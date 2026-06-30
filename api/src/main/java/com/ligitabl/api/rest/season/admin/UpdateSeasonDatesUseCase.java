package com.ligitabl.api.rest.season.admin;

import java.time.OffsetDateTime;
import java.util.UUID;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.ligitabl.model.repo.SeasonRepo;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Component
@RequiredArgsConstructor
@Slf4j
public class UpdateSeasonDatesUseCase {

    private final SeasonRepo seasonRepo;

    public sealed interface Result permits Result.Ok, Result.OutgoingSeasonNotFound, Result.UpcomingSeasonNotFound, Result.InvalidDateOrder {
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

        if (preSeasonOpensAt != null && predictionsOpenAt != null
                && !preSeasonOpensAt.isBefore(predictionsOpenAt)) {
            return new Result.InvalidDateOrder("preSeasonOpensAt must be before predictionsOpenAt");
        }

        if (outgoingSeasonId != null) {
            var outgoing = seasonRepo.findById(outgoingSeasonId).orElse(null);
            if (outgoing == null) return new Result.OutgoingSeasonNotFound(outgoingSeasonId);
            outgoing.setPreSeasonOpensAt(preSeasonOpensAt);
            seasonRepo.save(outgoing);
            log.info("[ADMIN_SEASON_DATES] outgoing={} preSeasonOpensAt={}", outgoingSeasonId, preSeasonOpensAt);
        }

        if (upcomingSeasonId != null) {
            var upcoming = seasonRepo.findById(upcomingSeasonId).orElse(null);
            if (upcoming == null) return new Result.UpcomingSeasonNotFound(upcomingSeasonId);
            upcoming.setPredictionsOpenAt(predictionsOpenAt);
            seasonRepo.save(upcoming);
            log.info("[ADMIN_SEASON_DATES] upcoming={} predictionsOpenAt={}", upcomingSeasonId, predictionsOpenAt);
        }

        return new Result.Ok();
    }
}
