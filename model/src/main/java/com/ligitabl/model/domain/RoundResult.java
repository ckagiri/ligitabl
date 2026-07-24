package com.ligitabl.model.domain;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import jakarta.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.SuperBuilder;

@Data
@EqualsAndHashCode(callSuper = true)
@SuperBuilder
public class RoundResult extends AbstractModel<UUID> {
    @NotNull
    private UUID roundSubmissionId;

    @NotNull
    private List<ResultTeamRank> rankings;

    private int totalScore;

    private int zeroesCount;

    private int swapCount;

    @Builder.Default
    private boolean userViewed = false;

    private Instant createdAt;

    public int getTotalHits() {
        return rankings.stream().mapToInt(ResultTeamRank::getHit).sum();
    }

    public HitDistribution hitDistribution() {
        int perfect = 0;
        int closeCalls = 0;
        int nearMisses = 0;
        int bigMisses = 0;

        for (ResultTeamRank ranking : rankings) {
            int hit = ranking.getHit();
            if (hit == 0) {
                perfect++;
            } else if (hit <= 2) {
                closeCalls++;
            } else if (hit <= 4) {
                nearMisses++;
            } else {
                bigMisses++;
            }
        }

        return new HitDistribution(perfect, closeCalls, nearMisses, bigMisses);
    }
}
