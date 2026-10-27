package com.ligitabl.api.usecase.match.shared;

import java.time.LocalDate;
import java.util.List;

import com.ligitabl.api.shared.Either;

/** The external source of fixtures, kickoff times and live scores. */
public interface MatchFeed {
    Either<MatchFeedError, List<FeedMatch>> liveMatches(String competitionCode);

    Either<MatchFeedError, List<FeedMatch>> matchesOn(String competitionCode, LocalDate date);

    Either<MatchFeedError, List<FeedMatch>> matchesBetween(String competitionCode, LocalDate from, LocalDate to);

    Either<MatchFeedError, List<FeedMatch>> matchesByIds(String competitionCode, List<Integer> ids);

    Either<MatchFeedError, FeedMatch> match(int id);

    Either<MatchFeedError, Integer> currentMatchday(String competitionCode);
}
