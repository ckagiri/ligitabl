package com.ligitabl.api.web.leaderboard;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.assertj.core.groups.Tuple;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.ligitabl.api.config.CompetitionDefaults;
import com.ligitabl.model.auth.PublicId;
import com.ligitabl.model.domain.Competition;
import com.ligitabl.model.domain.CompetitionSlug;
import com.ligitabl.model.domain.ResultTeamRank;
import com.ligitabl.model.domain.Round;
import com.ligitabl.model.domain.RoundResult;
import com.ligitabl.model.domain.Season;
import com.ligitabl.model.domain.SeasonPrediction;
import com.ligitabl.model.domain.SeasonSlug;
import com.ligitabl.model.domain.Team;
import com.ligitabl.model.domain.TeamRank;
import com.ligitabl.model.domain.TeamSlug;
import com.ligitabl.model.domain.User;
import com.ligitabl.model.repo.CompetitionRepo;
import com.ligitabl.model.repo.RoundRepo;
import com.ligitabl.model.repo.RoundResultRepo;
import com.ligitabl.model.repo.RoundSubmissionRepo;
import com.ligitabl.model.repo.SeasonPredictionRepo;
import com.ligitabl.model.repo.SeasonRepo;
import com.ligitabl.model.repo.TeamRepo;
import com.ligitabl.model.repo.UserRepo;

@ExtendWith(MockitoExtension.class)
class GetUserDetailUseCaseTest {

    private static final String DEFAULT_COMPETITION_SLUG = "premier-league";

    @Mock
    private CompetitionRepo competitionRepo;

    @Mock
    private SeasonRepo seasonRepo;

    @Mock
    private RoundRepo roundRepo;

    @Mock
    private RoundSubmissionRepo roundSubmissionRepo;

    @Mock
    private RoundResultRepo roundResultRepo;

    @Mock
    private SeasonPredictionRepo seasonPredictionRepo;

    @Mock
    private TeamRepo teamRepo;

    @Mock
    private UserRepo userRepo;

    private GetUserDetailUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new GetUserDetailUseCase(
                competitionRepo,
                seasonRepo,
                roundRepo,
                roundSubmissionRepo,
                roundResultRepo,
                seasonPredictionRepo,
                teamRepo,
                userRepo,
                new CompetitionDefaults(DEFAULT_COMPETITION_SLUG));
    }

    @Test
    void effectiveToRound_present_but_roundResult_missing_returns_empty_predictions_for_past_round() {
        UUID competitionId = UUID.randomUUID();
        UUID seasonId = UUID.randomUUID();
        UUID currentRoundId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID submissionId = UUID.randomUUID();

        var competition = Competition.builder()
                .id(competitionId)
                .slug(CompetitionSlug.of(DEFAULT_COMPETITION_SLUG))
                .code("PL")
                .name("Premier League")
                .build();

        var season = Season.builder()
                .id(seasonId)
                .competitionId(competitionId)
                .slug(SeasonSlug.of("2024-25"))
                .name("2024/25")
                .currentRoundId(currentRoundId)
                .build();

        var currentRound = Round.builder()
                .id(currentRoundId)
                .seasonId(seasonId)
                .name("GW 5")
                .slug("gw-5")
                .position(5)
                .finalized(false)
                .build();

        var user = User.builder()
                .id(userId)
                .publicId(PublicId.create("23456789AB"))
                .displayName("Alice")
                .roles(Set.of())
                .emailVerified(true)
                .build();

        int effectiveToRound = 4;

        var submission = com.ligitabl.model.domain.RoundSubmission.builder()
                .id(submissionId)
                .userId(userId)
                .seasonId(seasonId)
                .roundPosition(effectiveToRound)
                .seasonPredictionId(UUID.randomUUID())
                .rankings(List.of(new TeamRank("ARS", 1)))
                .build();

        given(competitionRepo.findBySlug(DEFAULT_COMPETITION_SLUG)).willReturn(Optional.of(competition));
        given(seasonRepo.findActiveSeason(competitionId)).willReturn(Optional.of(season));
        given(roundRepo.findById(currentRoundId)).willReturn(Optional.of(currentRound));
        given(userRepo.findByPublicId(any())).willReturn(Optional.of(user));
        given(roundSubmissionRepo.findByUserAndSeasonAndRound(userId, seasonId, effectiveToRound))
                .willReturn(Optional.of(submission));
        given(roundResultRepo.findByRoundSubmissionId(submissionId)).willReturn(Optional.empty());

        var result = useCase.execute("23456789AB", effectiveToRound, null);

        assertThat(result.isRight()).isTrue();
        var payload = result.get();
        assertThat(payload.round()).isEqualTo(effectiveToRound);
        assertThat(payload.predictions()).isEmpty();
    }

    @Test
    void effectiveToRound_present_but_submission_missing_returns_empty_predictions_for_past_round() {
        UUID competitionId = UUID.randomUUID();
        UUID seasonId = UUID.randomUUID();
        UUID currentRoundId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        var competition = Competition.builder()
                .id(competitionId)
                .slug(CompetitionSlug.of(DEFAULT_COMPETITION_SLUG))
                .code("PL")
                .name("Premier League")
                .build();

        var season = Season.builder()
                .id(seasonId)
                .competitionId(competitionId)
                .slug(SeasonSlug.of("2024-25"))
                .name("2024/25")
                .currentRoundId(currentRoundId)
                .build();

        var currentRound = Round.builder()
                .id(currentRoundId)
                .seasonId(seasonId)
                .name("GW 7")
                .slug("gw-7")
                .position(7)
                .finalized(false)
                .build();

        var user = User.builder()
                .id(userId)
                .publicId(PublicId.create("23456789AE"))
                .displayName("Dana")
                .roles(Set.of())
                .emailVerified(true)
                .build();

        int effectiveToRound = 6;

        given(competitionRepo.findBySlug(DEFAULT_COMPETITION_SLUG)).willReturn(Optional.of(competition));
        given(seasonRepo.findActiveSeason(competitionId)).willReturn(Optional.of(season));
        given(roundRepo.findById(currentRoundId)).willReturn(Optional.of(currentRound));
        given(userRepo.findByPublicId(any())).willReturn(Optional.of(user));
        given(roundSubmissionRepo.findByUserAndSeasonAndRound(userId, seasonId, effectiveToRound))
                .willReturn(Optional.empty());

        var result = useCase.execute("23456789AE", effectiveToRound, null);

        assertThat(result.isRight()).isTrue();
        var payload = result.get();
        assertThat(payload.round()).isEqualTo(effectiveToRound);
        assertThat(payload.predictions()).isEmpty();
    }

    @Test
    void effectiveToRound_present_roundResult_found_returns_shortName_and_hits_in_order() {
        UUID competitionId = UUID.randomUUID();
        UUID seasonId = UUID.randomUUID();
        UUID currentRoundId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID submissionId = UUID.randomUUID();

        var competition = Competition.builder()
                .id(competitionId)
                .slug(CompetitionSlug.of(DEFAULT_COMPETITION_SLUG))
                .code("PL")
                .name("Premier League")
                .build();

        var season = Season.builder()
                .id(seasonId)
                .competitionId(competitionId)
                .slug(SeasonSlug.of("2024-25"))
                .name("2024/25")
                .currentRoundId(currentRoundId)
                .build();

        var currentRound = Round.builder()
                .id(currentRoundId)
                .seasonId(seasonId)
                .name("GW 5")
                .slug("gw-5")
                .position(5)
                .finalized(false)
                .build();

        var user = User.builder()
                .id(userId)
                .publicId(PublicId.create("23456789AC"))
                .displayName("Bob")
                .roles(Set.of())
                .emailVerified(true)
                .build();

        int effectiveToRound = 4;

        var submission = com.ligitabl.model.domain.RoundSubmission.builder()
                .id(submissionId)
                .userId(userId)
                .seasonId(seasonId)
                .roundPosition(effectiveToRound)
                .seasonPredictionId(UUID.randomUUID())
                .rankings(List.of(new TeamRank("ARS", 1), new TeamRank("MCI", 2)))
                .build();

        var resultRankings = List.of(
                new ResultTeamRank(new TeamRank("ARS", 1), 3, 0), new ResultTeamRank(new TeamRank("MCI", 2), 1, 3));

        var roundResult = RoundResult.builder()
                .id(UUID.randomUUID())
                .roundSubmissionId(submissionId)
                .rankings(resultRankings)
                .totalScore(10)
                .zeroesCount(1)
                .swapCount(0)
                .userViewed(false)
                .build();

        var arsenal = Team.builder()
                .id(UUID.randomUUID())
                .name("Arsenal")
                .shortName("ARS")
                .slug(TeamSlug.of("arsenal"))
                .tla("ARS")
                .build();

        var manCity = Team.builder()
                .id(UUID.randomUUID())
                .name("Manchester City")
                .shortName("MCI")
                .slug(TeamSlug.of("manchester-city"))
                .tla("MCI")
                .build();

        given(competitionRepo.findBySlug(DEFAULT_COMPETITION_SLUG)).willReturn(Optional.of(competition));
        given(seasonRepo.findActiveSeason(competitionId)).willReturn(Optional.of(season));
        given(roundRepo.findById(currentRoundId)).willReturn(Optional.of(currentRound));
        given(userRepo.findByPublicId(any())).willReturn(Optional.of(user));
        given(roundSubmissionRepo.findByUserAndSeasonAndRound(userId, seasonId, effectiveToRound))
                .willReturn(Optional.of(submission));
        given(roundResultRepo.findByRoundSubmissionId(submissionId)).willReturn(Optional.of(roundResult));
        given(teamRepo.findAllByCodes(eq(Set.of("ARS", "MCI")))).willReturn(List.of(arsenal, manCity));

        var result = useCase.execute("23456789AC", effectiveToRound, null);

        assertThat(result.isRight()).isTrue();
        var payload = result.get();
        assertThat(payload.round()).isEqualTo(effectiveToRound);
        assertThat(payload.predictions())
                .extracting(GetUserDetailUseCase.PredictionTeam::teamName, GetUserDetailUseCase.PredictionTeam::hit)
                .containsExactly(Tuple.tuple("ARS", 0), Tuple.tuple("MCI", 3));
    }

    @Test
    void effectiveToRound_absent_falls_back_to_seasonPrediction_currentRankings_without_hits() {
        UUID competitionId = UUID.randomUUID();
        UUID seasonId = UUID.randomUUID();
        UUID currentRoundId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        var competition = Competition.builder()
                .id(competitionId)
                .slug(CompetitionSlug.of(DEFAULT_COMPETITION_SLUG))
                .code("PL")
                .name("Premier League")
                .build();

        var season = Season.builder()
                .id(seasonId)
                .competitionId(competitionId)
                .slug(SeasonSlug.of("2024-25"))
                .name("2024/25")
                .currentRoundId(currentRoundId)
                .build();

        var currentRound = Round.builder()
                .id(currentRoundId)
                .seasonId(seasonId)
                .name("GW 9")
                .slug("gw-9")
                .position(9)
                .finalized(false)
                .build();

        var user = User.builder()
                .id(userId)
                .publicId(PublicId.create("23456789AD"))
                .displayName("Carol")
                .roles(Set.of())
                .emailVerified(true)
                .build();

        var seasonPrediction = SeasonPrediction.builder()
                .id(UUID.randomUUID())
                .userId(userId)
                .seasonId(seasonId)
                .initialRankings(List.of(new TeamRank("ARS", 1)))
                .currentRankings(List.of(new TeamRank("ARS", 1), new TeamRank("MCI", 2)))
                .build();

        var arsenal = Team.builder()
                .id(UUID.randomUUID())
                .name("Arsenal")
                .shortName("ARS")
                .slug(TeamSlug.of("arsenal"))
                .tla("ARS")
                .build();

        var manCity = Team.builder()
                .id(UUID.randomUUID())
                .name("Manchester City")
                .shortName("MCI")
                .slug(TeamSlug.of("manchester-city"))
                .tla("MCI")
                .build();

        given(competitionRepo.findBySlug(DEFAULT_COMPETITION_SLUG)).willReturn(Optional.of(competition));
        given(seasonRepo.findActiveSeason(competitionId)).willReturn(Optional.of(season));
        given(roundRepo.findById(currentRoundId)).willReturn(Optional.of(currentRound));
        given(userRepo.findByPublicId(any())).willReturn(Optional.of(user));
        given(seasonPredictionRepo.findByUserAndSeason(userId, seasonId)).willReturn(Optional.of(seasonPrediction));
        given(teamRepo.findAllByCodes(eq(Set.of("ARS", "MCI")))).willReturn(List.of(arsenal, manCity));

        var result = useCase.execute("23456789AD", null, null);

        assertThat(result.isRight()).isTrue();
        var payload = result.get();
        assertThat(payload.round()).isEqualTo(9);
        assertThat(payload.predictions())
                .extracting(GetUserDetailUseCase.PredictionTeam::teamName, GetUserDetailUseCase.PredictionTeam::hit)
                .containsExactly(Tuple.tuple("ARS", null), Tuple.tuple("MCI", null));
    }
}
