package com.ligitabl.api.rest.contest.getprivatecontest;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Service;

import com.ligitabl.api.shared.Either;
import com.ligitabl.api.web.contest.SegmentNodeDto;
import com.ligitabl.api.web.contest.SegmentTreeBuilder;
import com.ligitabl.model.domain.Competition;
import com.ligitabl.model.domain.Contest;
import com.ligitabl.model.domain.Entry;
import com.ligitabl.model.domain.RoundSpan;
import com.ligitabl.model.domain.Season;
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

        int currentPosition = resolveCurrentPosition(season);

        String segmentCode = query.selectedSegmentCode();
        var segmentResult = resolveSelectedSegment(contest, competition, segmentCode, currentPosition);
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

        return Either.right(new GetPrivateContestResult(
                contest,
                selectedSegment,
                selectedSegment.getCode(),
                leaderboard,
                contest.isOwnedBy(query.userId()),
                members,
                contest.getJoinCode(),
                segmentTree,
                contestDateLabel));
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

    private int resolveCurrentPosition(Season season) {
        if (season.getCurrentRoundId() == null) return 1;
        return roundRepo
                .findById(season.getCurrentRoundId())
                .map(r -> r.getPosition())
                .orElse(1);
    }

    private Either<GetPrivateContestError, RoundSpan> resolveSelectedSegment(
            Contest contest, Competition competition, String segmentCode, int currentPosition) {

        if (segmentCode == null) {
            return Either.right(findCurrentSprint(contest, competition, currentPosition));
        }

        if ("overall".equalsIgnoreCase(segmentCode)) {
            return Either.right(RoundSpan.builder()
                    .code("overall")
                    .name("Overall")
                    .from(contest.getFromRoundPosition())
                    .to(contest.getToRoundPosition())
                    .build());
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

    private RoundSpan findCurrentSprint(Contest contest, Competition competition, int currentPos) {
        List<RoundSpan> contestSprints = (competition != null && competition.getPhases() != null
                        ? competition.getPhases()
                        : List.<RoundSpan>of())
                .stream()
                        .filter(p -> p.getType() == com.ligitabl.model.domain.PhaseType.SPRINT)
                        .filter(p -> p.getFrom() >= contest.getFromRoundPosition()
                                && p.getTo() <= contest.getToRoundPosition())
                        .sorted(Comparator.comparingInt(RoundSpan::getFrom))
                        .toList();

        return contestSprints.stream()
                .filter(s -> currentPos >= s.getFrom() && currentPos <= s.getTo())
                .findFirst()
                .or(() -> currentPos < contest.getFromRoundPosition()
                        ? contestSprints.stream().findFirst()
                        : Optional.empty())
                .orElse(contestSprints.getLast());
    }

    private boolean isSegmentLive(RoundSpan segment, int currentPosition) {
        return currentPosition >= segment.getFrom() && currentPosition <= segment.getTo();
    }
}
