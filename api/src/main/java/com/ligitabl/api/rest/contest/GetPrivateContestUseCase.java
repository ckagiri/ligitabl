package com.ligitabl.api.rest.contest;

import java.util.List;

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
        Contest contest = contestRepo.findById(query.contestId()).orElse(null);
        if (contest == null)
            return Either.left(new GetPrivateContestError.ContestNotFound(query.contestId()));

        Entry membership = entryRepo
                .findByUserAndContest(query.userId(), contest.getId())
                .orElse(null);
        if (membership == null || membership.getRemovedAtRound() != null)
            return Either.left(new GetPrivateContestError.NotAMember(query.userId(), contest.getId()));

        Season season = seasonRepo.findById(contest.getSeasonId()).orElse(null);
        if (season == null) return Either.left(new GetPrivateContestError.SeasonNotFound());

        Competition competition = competitionRepo.findAll().stream()
                .filter(c -> c.getActiveSeasonId() != null
                        && c.getActiveSeasonId().equals(contest.getSeasonId()))
                .findFirst()
                .orElse(null);

        int currentPosition = resolveCurrentPosition(season);

        var segmentResult = resolveSelectedSegment(
                contest, competition, query.selectedSegmentCode(), currentPosition);
        if (segmentResult.isLeft()) return segmentResult.map(s -> null);
        RoundSpan selectedSegment = segmentResult.get();

        boolean activeOnly = isSegmentLive(selectedSegment, currentPosition);

        var leaderboard = leaderboardRepo.computeLeaderboard(
                contest.getId(), season.getId(),
                selectedSegment.getFrom(), selectedSegment.getTo(),
                query.userId(), 0, 100, activeOnly);

        List<Entry> members = entryRepo.findByContestId(contest.getId());

        List<SegmentNodeDto> segmentTree =
                segmentTreeBuilder.build(contest, competition, query.userId(), currentPosition);

        return Either.right(new GetPrivateContestResult(
                contest, selectedSegment, leaderboard,
                contest.isOwnedBy(query.userId()), members, contest.getJoinCode(),
                segmentTree));
    }

    private int resolveCurrentPosition(Season season) {
        if (season.getCurrentRoundId() == null) return 1;
        return roundRepo.findById(season.getCurrentRoundId())
                .map(r -> r.getPosition())
                .orElse(1);
    }

    private Either<GetPrivateContestError, RoundSpan> resolveSelectedSegment(
            Contest contest, Competition competition, String segmentCode, int currentPosition) {

        if ("overall".equalsIgnoreCase(segmentCode)) {
            return Either.right(RoundSpan.builder()
                    .code("overall")
                    .name("Overall")
                    .from(contest.getFromRoundPosition())
                    .to(contest.getToRoundPosition())
                    .build());
        }

        List<RoundSpan> phases = competition != null && competition.getPhases() != null
                ? competition.getPhases()
                : List.of();

        RoundSpan segment = phases.stream()
                .filter(p -> p.getCode().equalsIgnoreCase(segmentCode))
                .filter(p -> p.getFrom() >= contest.getFromRoundPosition()
                        && p.getTo() <= contest.getToRoundPosition())
                .findFirst()
                .orElse(null);

        if (segment == null)
            return Either.left(new GetPrivateContestError.SegmentNotFound(segmentCode));

        return Either.right(segment);
    }

    private boolean isSegmentLive(RoundSpan segment, int currentPosition) {
        return currentPosition >= segment.getFrom() && currentPosition <= segment.getTo();
    }
}
