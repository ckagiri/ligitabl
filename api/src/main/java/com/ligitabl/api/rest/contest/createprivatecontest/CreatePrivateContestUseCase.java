package com.ligitabl.api.rest.contest.createprivatecontest;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ligitabl.api.contest.ContestCodeGenerator;
import com.ligitabl.api.shared.Either;
import com.ligitabl.model.domain.Contest;
import com.ligitabl.model.domain.Entry;
import com.ligitabl.model.domain.PhaseType;
import com.ligitabl.model.domain.Round;
import com.ligitabl.model.domain.RoundSpan;
import com.ligitabl.model.domain.RoundStatus;
import com.ligitabl.model.repo.CompetitionRepo;
import com.ligitabl.model.repo.ContestRepo;
import com.ligitabl.model.repo.EntryRepo;
import com.ligitabl.model.repo.MatchRepo;
import com.ligitabl.model.repo.RoundRepo;
import com.ligitabl.model.repo.SeasonRepo;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class CreatePrivateContestUseCase {

    private static final int MAX_PRIVATE_CONTESTS = 20;

    private final CompetitionRepo competitionRepo;
    private final SeasonRepo seasonRepo;
    private final RoundRepo roundRepo;
    private final MatchRepo matchRepo;
    private final ContestRepo contestRepo;
    private final EntryRepo entryRepo;
    private final ContestCodeGenerator codeGenerator;

    @Transactional
    public Either<CreatePrivateContestError, CreatePrivateContestResult> execute(
            CreatePrivateContestCommand cmd) {
        var competition = competitionRepo.findBySlug(cmd.competitionSlug()).orElse(null);
        if (competition == null)
            return Either.left(new CreatePrivateContestError.CompetitionNotFound(cmd.competitionSlug()));

        var season = seasonRepo.findActiveSeason(competition.getId()).orElse(null);
        if (season == null) return Either.left(new CreatePrivateContestError.SeasonNotFound());

        var currentRound = roundRepo.findById(season.getCurrentRoundId()).orElse(null);
        if (currentRound == null) return Either.left(new CreatePrivateContestError.CurrentRoundNotFound());

        List<RoundSpan> phases = competition.getPhases() != null ? competition.getPhases() : List.of();

        var fromResult = validateFromSprint(cmd.fromSprintCode(), phases, currentRound);
        if (fromResult.isLeft()) return fromResult.map(s -> null);
        RoundSpan fromSprint = fromResult.get();

        var toResult = validateToSprint(cmd.toSprintCode(), fromSprint, phases);
        if (toResult.isLeft()) return toResult.map(s -> null);
        RoundSpan toSprint = toResult.get();

        if (contestRepo.findPrivateByUserId(cmd.userId()).size() >= MAX_PRIVATE_CONTESTS)
            return Either.left(new CreatePrivateContestError.MaxContestsReached(MAX_PRIVATE_CONTESTS));

        String joinCode = generateUniqueCode();

        Contest contest = Contest.builder()
                .seasonId(season.getId())
                .name(cmd.name())
                .isPrivate(true)
                .isOpen(true)
                .joinCode(joinCode)
                .fromRoundPosition(fromSprint.getFrom())
                .toRoundPosition(toSprint.getTo())
                .maxEntries(cmd.maxEntries())
                .ownerId(cmd.userId())
                .build();

        Contest saved = contestRepo.save(contest);
        entryRepo.save(Entry.builder()
                .userId(cmd.userId())
                .contestId(saved.getId())
                .joinedAtRound(currentRound.getPosition())
                .build());

        log.info("User {} created private contest {} with code {}", cmd.userId(), saved.getId(), joinCode);
        return Either.right(new CreatePrivateContestResult(saved.getId(), joinCode));
    }

    private Either<CreatePrivateContestError, RoundSpan> validateFromSprint(
            String fromCode, List<RoundSpan> phases, Round currentRound) {

        RoundSpan sprint = phases.stream()
                .filter(p -> p.getType() == PhaseType.SPRINT)
                .filter(s -> s.getCode().equalsIgnoreCase(fromCode))
                .findFirst()
                .orElse(null);

        if (sprint == null) return Either.left(new CreatePrivateContestError.InvalidFromSprint(fromCode));

        int pos = currentRound.getPosition();
        if (sprint.getTo() < pos) return Either.left(new CreatePrivateContestError.InvalidFromSprint(fromCode));

        // Sprint's last round is current — only valid if that round is still OPEN
        if (sprint.getTo() == pos) {
            var matches = matchRepo.findByRoundId(currentRound.getId());
            RoundStatus status = currentRound.isFinalized()
                    ? RoundStatus.FINALIZED
                    : currentRound.computeStatus(matches);
            if (status != RoundStatus.OPEN)
                return Either.left(new CreatePrivateContestError.InvalidFromSprint(fromCode));
        }

        return Either.right(sprint);
    }

    private Either<CreatePrivateContestError, RoundSpan> validateToSprint(
            String toCode, RoundSpan fromSprint, List<RoundSpan> phases) {

        RoundSpan toSprint = phases.stream()
                .filter(p -> p.getType() == PhaseType.SPRINT)
                .filter(s -> s.getCode().equalsIgnoreCase(toCode))
                .findFirst()
                .orElse(null);

        if (toSprint == null)
            return Either.left(
                    new CreatePrivateContestError.InvalidToCombination(fromSprint.getCode(), toCode));

        if (fromSprint.getCode().equalsIgnoreCase(toCode)) return Either.right(toSprint);

        // Multi-sprint: FROM must be a quarter-start, TO must be a quarter-end, TO after FROM
        List<RoundSpan> quarters =
                phases.stream().filter(p -> p.getType() == PhaseType.QUARTER).toList();

        boolean fromIsQuarterStart = quarters.stream()
                .anyMatch(q -> fromSprint.getFrom() == q.getFrom()
                        && fromSprint.getTo() <= q.getTo());

        boolean toIsQuarterEnd = quarters.stream()
                .anyMatch(q -> toSprint.getTo() == q.getTo()
                        && toSprint.getFrom() >= q.getFrom());

        boolean toIsAfterFrom = toSprint.getFrom() > fromSprint.getTo();

        if (!fromIsQuarterStart || !toIsQuarterEnd || !toIsAfterFrom)
            return Either.left(
                    new CreatePrivateContestError.InvalidToCombination(fromSprint.getCode(), toCode));

        return Either.right(toSprint);
    }

    private String generateUniqueCode() {
        String code;
        do {
            code = codeGenerator.generate();
        } while (contestRepo.findByJoinCode(code).isPresent());
        return code;
    }
}
