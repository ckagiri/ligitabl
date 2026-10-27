package com.ligitabl.api.client;

import java.time.LocalDate;
import java.util.List;

import org.springframework.stereotype.Component;

import com.ligitabl.api.client.footballdata.MatchDto;
import com.ligitabl.api.client.footballdata.MatchesResponse;
import com.ligitabl.api.shared.Either;
import com.ligitabl.api.usecase.match.shared.FeedMatch;
import com.ligitabl.api.usecase.match.shared.MatchFeed;
import com.ligitabl.api.usecase.match.shared.MatchFeedError;
import com.ligitabl.model.domain.MatchStatus;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/** {@link MatchFeed} over football-data.org: maps its DTOs, status strings and errors to ours. */
@Component
@RequiredArgsConstructor
@Slf4j
public class FootballDataMatchFeed implements MatchFeed {

    private final FootballDataClient client;

    @Override
    public Either<MatchFeedError, List<FeedMatch>> liveMatches(String competitionCode) {
        return toMatches(client.getLiveMatches(competitionCode));
    }

    @Override
    public Either<MatchFeedError, List<FeedMatch>> matchesOn(String competitionCode, LocalDate date) {
        return toMatches(client.getMatchesForDate(competitionCode, date));
    }

    @Override
    public Either<MatchFeedError, List<FeedMatch>> matchesBetween(
            String competitionCode, LocalDate from, LocalDate to) {
        return toMatches(client.getMatchesInDateRange(competitionCode, from, to));
    }

    @Override
    public Either<MatchFeedError, List<FeedMatch>> matchesByIds(String competitionCode, List<Integer> ids) {
        return toMatches(client.getMatchesByIds(competitionCode, ids));
    }

    @Override
    public Either<MatchFeedError, FeedMatch> match(int id) {
        return client.getMatchById(id)
                .mapLeft(FootballDataMatchFeed::toFeedError)
                .map(this::toFeedMatch);
    }

    @Override
    public Either<MatchFeedError, Integer> currentMatchday(String competitionCode) {
        return client.getCompetition(competitionCode)
                .mapLeft(FootballDataMatchFeed::toFeedError)
                .flatMap(competition -> {
                    var matchday = competition.currentSeason().currentMatchday();
                    return matchday == null
                            ? Either.left(new MatchFeedError.Rejected("No current matchday for " + competitionCode))
                            : Either.right(matchday);
                });
    }

    MatchStatus toDomainStatus(String status) {
        return switch (status) {
            case "SCHEDULED", "TIMED" -> MatchStatus.SCHEDULED;
            case "IN_PLAY", "PAUSED" -> MatchStatus.LIVE;
            case "FINISHED", "AWARDED" -> MatchStatus.FINISHED;
            case "SUSPENDED" -> MatchStatus.SUSPENDED;
            case "POSTPONED" -> MatchStatus.POSTPONED;
            case "CANCELLED" -> MatchStatus.CANCELLED;
            default -> {
                log.warn("Unknown match status from API: {}", status);
                yield MatchStatus.SCHEDULED;
            }
        };
    }

    private Either<MatchFeedError, List<FeedMatch>> toMatches(Either<FootballDataApiError, MatchesResponse> response) {
        return response.mapLeft(FootballDataMatchFeed::toFeedError)
                .map(r -> r.matches().stream().map(this::toFeedMatch).toList());
    }

    private FeedMatch toFeedMatch(MatchDto dto) {
        var fullTime = dto.score() == null ? null : dto.score().fullTime();
        return new FeedMatch(
                dto.id(),
                toDomainStatus(dto.status()),
                dto.utcDate(),
                dto.matchday(),
                dto.minute(),
                dto.injuryTime(),
                fullTime == null ? null : fullTime.home(),
                fullTime == null ? null : fullTime.away());
    }

    static MatchFeedError toFeedError(FootballDataApiError error) {
        return switch (error) {
            case FootballDataApiError.NetworkError e -> new MatchFeedError.Unavailable(e.message());
            case FootballDataApiError.ServerError e -> new MatchFeedError.Unavailable(e.message());
            case FootballDataApiError.RateLimitExceeded e -> new MatchFeedError.Unavailable(e.message());
            case FootballDataApiError.Unauthorized e -> new MatchFeedError.Rejected(e.message());
            case FootballDataApiError.NotFound e -> new MatchFeedError.Rejected(e.message());
            case FootballDataApiError.UnknownError e -> new MatchFeedError.Rejected(e.message());
            case FootballDataApiError.UnexpectedError e -> new MatchFeedError.Rejected(e.message());
        };
    }
}
