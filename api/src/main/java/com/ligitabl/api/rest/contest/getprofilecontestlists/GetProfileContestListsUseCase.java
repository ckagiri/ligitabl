package com.ligitabl.api.rest.contest.getprofilecontestlists;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;

import org.springframework.stereotype.Service;

import com.ligitabl.api.config.CompetitionDefaults;
import com.ligitabl.api.rest.contest.shared.ContestRankResolver;
import com.ligitabl.model.domain.PhaseRules;
import com.ligitabl.model.domain.RoundSpan;
import com.ligitabl.model.domain.Season;
import com.ligitabl.model.repo.CompetitionRepo;
import com.ligitabl.model.repo.ContestRepo;
import com.ligitabl.model.repo.EntryRepo;
import com.ligitabl.model.repo.SeasonRepo;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class GetProfileContestListsUseCase {

    private static final int PAGE_SIZE = 10;

    private final CompetitionDefaults competitionDefaults;
    private final CompetitionRepo competitionRepo;
    private final SeasonRepo seasonRepo;
    private final ContestRepo contestRepo;
    private final EntryRepo entryRepo;
    private final ContestRankResolver contestRankResolver;

    public GetProfileContestListsResult execute(GetProfileContestListsQuery query) {
        UUID activeSeasonId = seasonRepo
                .findActiveSeason(competitionDefaults.defaultCompetitionSlug())
                .map(Season::getId)
                .orElse(null);

        // Phase structure (sprints/quarters) is fixed per competition, not per season, so one
        // fetch covers every row below regardless of which season each contest belongs to.
        List<RoundSpan> phases = competitionRepo
                .findBySlug(competitionDefaults.defaultCompetitionSlug())
                .map(c -> c.getPhases() != null ? c.getPhases() : List.<RoundSpan>of())
                .orElse(List.of());

        int activePage = Math.max(1, query.activePage());
        int pastPage = Math.max(1, query.pastPage());
        int activeOffset = (activePage - 1) * PAGE_SIZE;
        int pastOffset = (pastPage - 1) * PAGE_SIZE;

        int activeTotal = contestRepo.countContestsByUserId(query.userId(), activeSeasonId, true);
        int pastTotal = contestRepo.countContestsByUserId(query.userId(), activeSeasonId, false);

        List<ContestRepo.UserContestView> activeViews =
                contestRepo.findContestsByUserId(query.userId(), activeSeasonId, true, PAGE_SIZE, activeOffset);
        List<ContestRepo.UserContestView> pastViews =
                contestRepo.findContestsByUserId(query.userId(), activeSeasonId, false, PAGE_SIZE, pastOffset);

        Map<UUID, Integer> memberCounts = entryRepo.countActiveByContestIds(Stream.concat(
                        activeViews.stream(), pastViews.stream())
                .map(ContestRepo.UserContestView::contestId)
                .toList());

        List<ContestSummary> activeContests =
                activeViews.stream().map(v -> toSummary(v, query.userId(), memberCounts, phases)).toList();

        List<ContestSummary> pastContests =
                pastViews.stream().map(v -> toSummary(v, query.userId(), memberCounts, phases)).toList();

        return new GetProfileContestListsResult(
                activeContests,
                activeTotal,
                (int) Math.ceil((double) activeTotal / PAGE_SIZE),
                activeTotal == 0 ? 0 : activeOffset + 1,
                Math.min(activeOffset + PAGE_SIZE, activeTotal),
                pastContests,
                pastTotal,
                (int) Math.ceil((double) pastTotal / PAGE_SIZE),
                pastTotal == 0 ? 0 : pastOffset + 1,
                Math.min(pastOffset + PAGE_SIZE, pastTotal));
    }

    private ContestSummary toSummary(
            ContestRepo.UserContestView view, UUID userId, Map<UUID, Integer> memberCounts, List<RoundSpan> phases) {
        int memberCount = memberCounts.getOrDefault(view.contestId(), 0);
        Integer rank = resolveRank(view, userId);
        String link = "/contests/" + view.contestId() + (view.isPrivate() ? "?from=profile" : "?segment=overall&from=profile");
        String periodLabel = PhaseRules.resolvePeriodLabel(phases, view.fromRoundPosition(), view.toRoundPosition());
        return new ContestSummary(view.contestName(), view.seasonName(), periodLabel, memberCount, rank, link);
    }

    private Integer resolveRank(ContestRepo.UserContestView view, UUID userId) {
        try {
            return contestRankResolver
                    .resolve(
                            view.contestId(), view.seasonId(), view.fromRoundPosition(), view.toRoundPosition(), userId)
                    .position();
        } catch (Exception e) {
            log.warn("Could not resolve rank for user {} in contest {}: {}", userId, view.contestId(), e.getMessage());
            return null;
        }
    }
}
