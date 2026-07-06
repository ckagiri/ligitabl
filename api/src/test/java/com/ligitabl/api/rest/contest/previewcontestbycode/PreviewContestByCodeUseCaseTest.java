package com.ligitabl.api.rest.contest.previewcontestbycode;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.ligitabl.model.domain.*;
import com.ligitabl.model.repo.*;

@ExtendWith(MockitoExtension.class)
class PreviewContestByCodeUseCaseTest {

    @Mock
    ContestRepo contestRepo;

    @Mock
    EntryRepo entryRepo;

    @Mock
    SeasonRepo seasonRepo;

    @Mock
    CompetitionRepo competitionRepo;

    @Mock
    MatchRepo matchRepo;

    private PreviewContestByCodeUseCase useCase;

    private static final String CODE = "AB3K7PQ";

    private UUID seasonId;
    private UUID competitionId;
    private Contest contest;
    private Season season;
    private Competition competition;

    @BeforeEach
    void setUp() {
        useCase = new PreviewContestByCodeUseCase(contestRepo, entryRepo, seasonRepo, competitionRepo, matchRepo);

        seasonId = UUID.randomUUID();
        competitionId = UUID.randomUUID();

        contest = Contest.builder()
                .id(UUID.randomUUID())
                .seasonId(seasonId)
                .name("Office Rivals")
                .isPrivate(true)
                .isOpen(true)
                .joinCode(CODE)
                .fromRoundPosition(1)
                .toRoundPosition(5)
                .build();

        season = Season.builder()
                .id(seasonId)
                .competitionId(competitionId)
                .name("2025/26")
                .slug(SeasonSlug.of("2025-26"))
                .clientId(1)
                .maxRounds(20)
                .build();

        competition = Competition.builder()
                .id(competitionId)
                .name("Premier League")
                .slug(CompetitionSlug.of("premier-league"))
                .code("PL")
                .phases(List.of(RoundSpan.builder()
                        .code("S1")
                        .name("Sprint 1")
                        .type(PhaseType.SPRINT)
                        .from(1)
                        .to(5)
                        .build()))
                .build();
    }

    @Test
    void codeNotFound_returnsContestNotFound() {
        when(contestRepo.findByJoinCode(CODE)).thenReturn(Optional.empty());

        var result = useCase.execute(CODE);

        assertThat(result.isLeft()).isTrue();
        assertThat(result.getLeft()).isInstanceOf(PreviewContestByCodeError.ContestNotFound.class);
    }

    @Test
    void contestClosed_returnsContestClosed() {
        Contest closed = Contest.builder()
                .id(contest.getId())
                .seasonId(seasonId)
                .name("Office Rivals")
                .isPrivate(true)
                .isOpen(false)
                .joinCode(CODE)
                .fromRoundPosition(1)
                .toRoundPosition(5)
                .build();
        when(contestRepo.findByJoinCode(CODE)).thenReturn(Optional.of(closed));
        when(seasonRepo.findById(seasonId)).thenReturn(Optional.of(season));

        var result = useCase.execute(CODE);

        assertThat(result.isLeft()).isTrue();
        assertThat(result.getLeft()).isInstanceOf(PreviewContestByCodeError.ContestClosed.class);
    }

    @Test
    void seasonOffSeason_returnsContestClosed() {
        Season offSeason = Season.builder()
                .id(seasonId)
                .competitionId(competitionId)
                .name("2024/25")
                .slug(SeasonSlug.of("2024-25"))
                .clientId(1)
                .maxRounds(20)
                .completed(true)
                .endDate(LocalDate.now().minusDays(1))
                .build();
        when(contestRepo.findByJoinCode(CODE)).thenReturn(Optional.of(contest));
        when(seasonRepo.findById(seasonId)).thenReturn(Optional.of(offSeason));

        var result = useCase.execute(CODE);

        assertThat(result.isLeft()).isTrue();
        assertThat(result.getLeft()).isInstanceOf(PreviewContestByCodeError.ContestClosed.class);
    }

    @Test
    void seasonNotFound_returnsCompetitionNotFound() {
        when(contestRepo.findByJoinCode(CODE)).thenReturn(Optional.of(contest));
        when(seasonRepo.findById(seasonId)).thenReturn(Optional.empty());

        var result = useCase.execute(CODE);

        assertThat(result.isLeft()).isTrue();
        assertThat(result.getLeft()).isInstanceOf(PreviewContestByCodeError.CompetitionNotFound.class);
    }

    @Test
    void happyPath_currentSeason_returnsPreview() {
        when(contestRepo.findByJoinCode(CODE)).thenReturn(Optional.of(contest));
        when(seasonRepo.findById(seasonId)).thenReturn(Optional.of(season));
        when(competitionRepo.findById(competitionId)).thenReturn(Optional.of(competition));
        when(entryRepo.countActiveByContestId(contest.getId())).thenReturn(4);
        when(matchRepo.groupRoundDateRangesBySeason(seasonId)).thenReturn(Map.of());

        var result = useCase.execute(CODE);

        assertThat(result.isRight()).isTrue();
        assertThat(result.get().name()).isEqualTo("Office Rivals");
        assertThat(result.get().memberCount()).isEqualTo(4);
        assertThat(result.get().isOpen()).isTrue();
    }
}
