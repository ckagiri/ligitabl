package com.ligitabl.api.web.shared.season;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import com.ligitabl.api.web.shared.share.SharePredictionTextBuilder;
import com.ligitabl.model.auth.PublicId;
import com.ligitabl.model.domain.Round;
import com.ligitabl.model.domain.Season;
import com.ligitabl.model.domain.SeasonPrediction;
import com.ligitabl.model.domain.SeasonSlug;
import com.ligitabl.model.domain.TeamRank;
import com.ligitabl.model.domain.User;
import com.ligitabl.model.repo.RoundRepo;
import com.ligitabl.model.repo.SeasonPredictionRepo;
import com.ligitabl.model.repo.SeasonRepo;
import com.ligitabl.model.repo.TeamRepo;

@ExtendWith(MockitoExtension.class)
class SeasonPredictionSupportTest {

    @Mock
    private SeasonRepo seasonRepo;

    @Mock
    private RoundRepo roundRepo;

    @Mock
    private SeasonPredictionRepo seasonPredictionRepo;

    @Mock
    private TeamRepo teamRepo;

    private SeasonPredictionSupport support;

    private UUID seasonId;
    private UUID roundId;
    private UUID userId;
    private User user;

    @BeforeEach
    void setUp() {
        support = new SeasonPredictionSupport(
                seasonRepo, roundRepo, seasonPredictionRepo, teamRepo, new SharePredictionTextBuilder());
        ReflectionTestUtils.setField(support, "frontendShareUrl", "https://ligipredictor.com");

        seasonId = UUID.randomUUID();
        roundId = UUID.randomUUID();
        userId = UUID.randomUUID();
        user = User.builder()
                .id(userId)
                .publicId(PublicId.create("T2ADsSc8hQ"))
                .displayName("Jane Doe")
                .build();

        lenient().when(teamRepo.findAllByCodes(any())).thenReturn(List.of());
    }

    @Test
    void noActiveSeason_hidden() {
        when(seasonRepo.findActiveSeason("premier-league")).thenReturn(Optional.empty());

        var data = support.buildShareData(user, "premier-league");

        assertThat(data.visible()).isFalse();
        assertThat(data.shareUrl()).isNull();
        assertThat(data.shareText()).isNull();
    }

    @Test
    void noPredictionForUser_hidden() {
        Season season = inPlaySeason();
        when(seasonRepo.findActiveSeason("premier-league")).thenReturn(Optional.of(season));
        when(seasonPredictionRepo.findByUserAndSeason(userId, seasonId)).thenReturn(Optional.empty());

        var data = support.buildShareData(user, "premier-league");

        assertThat(data.visible()).isFalse();
    }

    @Test
    void inPlaySeasonWithCurrentRankings_visible() {
        Season season = inPlaySeason();
        SeasonPrediction prediction = SeasonPrediction.builder()
                .id(UUID.randomUUID())
                .userId(userId)
                .seasonId(seasonId)
                .currentRankings(List.of(TeamRank.of("ARS", 1), TeamRank.of("CHE", 2)))
                .atRoundNumber(5)
                .build();
        Round round = Round.builder().id(roundId).seasonId(seasonId).position(5).build();

        when(seasonRepo.findActiveSeason("premier-league")).thenReturn(Optional.of(season));
        when(seasonPredictionRepo.findByUserAndSeason(userId, seasonId)).thenReturn(Optional.of(prediction));
        when(roundRepo.findById(roundId)).thenReturn(Optional.of(round));

        var data = support.buildShareData(user, "premier-league");

        assertThat(data.visible()).isTrue();
        assertThat(data.shareUrl()).isEqualTo("https://ligipredictor.com/u/T2ADsSc8hQ/2526/gw/5");
        assertThat(data.shareText()).contains("1 ARS", "2 CHE").contains("ligipredictor.com/u/T2ADsSc8hQ/2526/gw/5");
    }

    @Test
    void preSeasonWithInitialRankings_visible() {
        Season season = preSeasonSeason();
        SeasonPrediction prediction = SeasonPrediction.builder()
                .id(UUID.randomUUID())
                .userId(userId)
                .seasonId(seasonId)
                .currentRankings(List.of(TeamRank.of("ARS", 1)))
                .initialRankings(List.of(TeamRank.of("ARS", 1)))
                .atRoundNumber(0)
                .build();
        Round round = Round.builder().id(roundId).seasonId(seasonId).position(1).build();

        when(seasonRepo.findActiveSeason("premier-league")).thenReturn(Optional.of(season));
        when(seasonPredictionRepo.findByUserAndSeason(userId, seasonId)).thenReturn(Optional.of(prediction));
        when(roundRepo.findById(roundId)).thenReturn(Optional.of(round));

        var data = support.buildShareData(user, "premier-league");

        assertThat(data.visible()).isTrue();
        assertThat(data.shareUrl()).isEqualTo("https://ligipredictor.com/u/T2ADsSc8hQ/2526/gw/1");
    }

    private Season inPlaySeason() {
        return Season.builder()
                .id(seasonId)
                .currentRoundId(roundId)
                .maxRounds(38)
                .slug(SeasonSlug.of("2025-26"))
                .startDate(LocalDate.now().minusMonths(1))
                .endDate(LocalDate.now().plusMonths(9))
                .completed(false)
                .predictionsOpenAt(null) // null => open, i.e. IN_PLAY
                .build();
    }

    private Season preSeasonSeason() {
        return Season.builder()
                .id(seasonId)
                .currentRoundId(roundId)
                .maxRounds(38)
                .slug(SeasonSlug.of("2025-26"))
                .startDate(LocalDate.now().plusMonths(1))
                .endDate(LocalDate.now().plusMonths(10))
                .completed(false)
                .preSeasonOpensAt(OffsetDateTime.now().minusDays(1))
                .predictionsOpenAt(OffsetDateTime.now().plusDays(10))
                .build();
    }
}
