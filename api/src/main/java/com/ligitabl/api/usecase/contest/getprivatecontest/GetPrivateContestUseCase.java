package com.ligitabl.api.usecase.contest.getprivatecontest;

import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;

import com.ligitabl.api.shared.Either;
import com.ligitabl.api.usecase.contest.shared.ContestSupport;
import com.ligitabl.api.usecase.contest.shared.SegmentNodeDto;
import com.ligitabl.api.usecase.contest.shared.SegmentTreeBuilder;
import com.ligitabl.model.domain.contest.Contest;
import com.ligitabl.model.domain.contest.Entry;
import com.ligitabl.model.domain.league.Competition;
import com.ligitabl.model.domain.league.PhaseRules;
import com.ligitabl.model.domain.league.PhaseType;
import com.ligitabl.model.domain.league.Round;
import com.ligitabl.model.domain.league.RoundSpan;
import com.ligitabl.model.domain.league.Season;
import com.ligitabl.model.repo.CompetitionRepo;
import com.ligitabl.model.repo.ContestRepo;
import com.ligitabl.model.repo.EntryRepo;
import com.ligitabl.model.repo.LeaderboardRepo;
import com.ligitabl.model.repo.RoundRepo;
import com.ligitabl.model.repo.SeasonRepo;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class GetPrivateContestUseCase {

    private final ContestRepo contestRepo;
    private final EntryRepo entryRepo;
    private final SeasonRepo seasonRepo;
    private final CompetitionRepo competitionRepo;
    private final LeaderboardRepo leaderboardRepo;
    private final RoundRepo roundRepo;
    private final SegmentTreeBuilder segmentTreeBuilder;
    private final ContestSupport contestSupport;

    public Either<GetPrivateContestError, GetPrivateContestResult> execute(GetPrivateContestQuery query) {
        var contestResult = resolveContest(query.contestId());
        if (contestResult.isLeft()) return contestResult.castLeft();
        Contest contest = contestResult.get();

        var membershipResult = validateMembership(query.userId(), contest.getId());
        if (membershipResult.isLeft()) return membershipResult.castLeft();

        var seasonResult = resolveSeason(contest.getSeasonId());
        if (seasonResult.isLeft()) return seasonResult.castLeft();
        Season season = seasonResult.get();

        var competitionResult = resolveCompetition(season);
        if (competitionResult.isLeft()) return competitionResult.castLeft();
        Competition competition = competitionResult.get();

        Round currentRound = resolveCurrentRound(season);
        int currentPosition = currentRound != null ? currentRound.getPosition() : 1;
        int effectivePosition = currentRound != null ? PhaseRules.effectivePosition(currentRound) : 1;

        String segmentCode = query.selectedSegmentCode();
        var segmentResult =
                resolveSelectedSegment(contest, competition, segmentCode, query.preferredLevel(), effectivePosition);
        if (segmentResult.isLeft()) return segmentResult.castLeft();
        RoundSpan selectedSegment = segmentResult.get();

        boolean activeOnly = isSegmentLive(selectedSegment, currentPosition);

        int pageSize = 10;
        int offset = query.page() * pageSize;
        var leaderboard = leaderboardRepo.computeLeaderboard(
                contest.getId(),
                season.getId(),
                selectedSegment.getFrom(),
                selectedSegment.getTo(),
                query.userId(),
                offset,
                pageSize,
                activeOnly);

        List<Entry> members = entryRepo.findByContestId(contest.getId());

        List<SegmentNodeDto> segmentTree =
                segmentTreeBuilder.build(contest, competition, query.userId(), currentPosition);

        String contestDateLabel = segmentTreeBuilder.contestDateLabel(
                contest.getSeasonId(), contest.getFromRoundPosition(), contest.getToRoundPosition());

        String contestPeriodLabel = resolveContestPeriodLabel(contest, competition);

        boolean isOpenForJoining = contestSupport.isOpenForJoining(contest, season, competition);

        return Either.right(new GetPrivateContestResult(
                contest,
                selectedSegment,
                selectedSegment.getCode(),
                leaderboard,
                contest.isOwnedBy(query.userId()),
                members,
                contest.getJoinCode(),
                segmentTree,
                contestDateLabel,
                contestPeriodLabel,
                isOpenForJoining));
    }

    private Either<GetPrivateContestError, Contest> resolveContest(UUID contestId) {
        return contestRepo
                .findById(contestId)
                .<Either<GetPrivateContestError, Contest>>map(Either::right)
                .orElseGet(() -> Either.left(new GetPrivateContestError.ContestNotFound(contestId)));
    }

    private Either<GetPrivateContestError, Entry> validateMembership(UUID userId, UUID contestId) {
        Entry membership = entryRepo.findByUserAndContest(userId, contestId).orElse(null);
        if (membership == null || membership.getRemovedAtRound() != null)
            return Either.left(new GetPrivateContestError.NotAMember(userId, contestId));
        return Either.right(membership);
    }

    private Either<GetPrivateContestError, Season> resolveSeason(UUID seasonId) {
        return seasonRepo
                .findById(seasonId)
                .<Either<GetPrivateContestError, Season>>map(Either::right)
                .orElseGet(() -> Either.left(new GetPrivateContestError.SeasonNotFound()));
    }

    private Either<GetPrivateContestError, Competition> resolveCompetition(Season season) {
        return competitionRepo
                .findById(season.getCompetitionId())
                .<Either<GetPrivateContestError, Competition>>map(Either::right)
                .orElseGet(() -> Either.left(new GetPrivateContestError.CompetitionNotFound(season.getId())));
    }

    private Round resolveCurrentRound(Season season) {
        if (season.getCurrentRoundId() == null) return null;
        return roundRepo.findById(season.getCurrentRoundId()).orElse(null);
    }

    private Either<GetPrivateContestError, RoundSpan> resolveSelectedSegment(
            Contest contest,
            Competition competition,
            String segmentCode,
            SegmentLevel preferredLevel,
            int effectivePosition) {

        if (segmentCode == null) {
            return Either.right(defaultSegment(contest, competition, preferredLevel, effectivePosition));
        }

        if ("overall".equalsIgnoreCase(segmentCode)) {
            return Either.right(overallSpan(contest));
        }

        List<RoundSpan> phases =
                competition != null && competition.getPhases() != null ? competition.getPhases() : List.of();

        RoundSpan segment = phases.stream()
                .filter(p -> p.getCode().equalsIgnoreCase(segmentCode))
                .filter(p -> p.getFrom() >= contest.getFromRoundPosition() && p.getTo() <= contest.getToRoundPosition())
                .findFirst()
                .orElse(null);

        if (segment == null) return Either.left(new GetPrivateContestError.SegmentNotFound(segmentCode));

        return Either.right(segment);
    }

    // The preferred level's current segment; a level this contest lacks falls back to the current sprint.
    private RoundSpan defaultSegment(
            Contest contest, Competition competition, SegmentLevel preferredLevel, int effectivePosition) {
        // Matches SegmentTreeBuilder: only a contest spanning several quarters has an Overall node.
        if (preferredLevel == SegmentLevel.OVERALL && quartersInWindow(contest, competition) > 1) {
            return overallSpan(contest);
        }
        if (preferredLevel == SegmentLevel.QUARTER) {
            var quarter = contestSupport.findCurrentPhase(contest, competition, PhaseType.QUARTER, effectivePosition);
            if (quarter.isPresent()) return quarter.get();
        }
        return contestSupport.findCurrentSprint(contest, competition, effectivePosition);
    }

    private static long quartersInWindow(Contest contest, Competition competition) {
        List<RoundSpan> phases =
                competition != null && competition.getPhases() != null ? competition.getPhases() : List.of();
        return phases.stream()
                .filter(p -> p.getType() == PhaseType.QUARTER)
                .filter(p -> p.getFrom() >= contest.getFromRoundPosition() && p.getTo() <= contest.getToRoundPosition())
                .count();
    }

    private static RoundSpan overallSpan(Contest contest) {
        return RoundSpan.builder()
                .code("overall")
                .name("Overall")
                .from(contest.getFromRoundPosition())
                .to(contest.getToRoundPosition())
                .build();
    }

    private boolean isSegmentLive(RoundSpan segment, int currentPosition) {
        return currentPosition >= segment.getFrom() && currentPosition <= segment.getTo();
    }

    private String resolveContestPeriodLabel(Contest contest, Competition competition) {
        List<RoundSpan> phases = competition.getPhases() != null ? competition.getPhases() : List.of();
        return PhaseRules.resolvePeriodLabel(phases, contest.getFromRoundPosition(), contest.getToRoundPosition());
    }
}
