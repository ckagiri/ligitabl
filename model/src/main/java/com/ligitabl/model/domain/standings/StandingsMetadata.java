package com.ligitabl.model.domain.standings;

import lombok.Builder;
import lombok.Value;
import lombok.extern.jackson.Jacksonized;

@Value
@Builder
@Jacksonized
public class StandingsMetadata {
    int played;
    int won;
    int drawn;
    int lost;
    int points;
    int gf;
    int ga;
    int gd;

    public StandingsMetadata(int played, int won, int drawn, int lost, int points, int gf, int ga, int gd) {
        if (played < 0) throw new IllegalArgumentException("Played cannot be negative");
        if (won < 0) throw new IllegalArgumentException("Won cannot be negative");
        if (drawn < 0) throw new IllegalArgumentException("Drawn cannot be negative");
        if (lost < 0) throw new IllegalArgumentException("Lost cannot be negative");
        if (points < 0) throw new IllegalArgumentException("Points cannot be negative");
        if (gf < 0) throw new IllegalArgumentException("GF cannot be negative");
        if (ga < 0) throw new IllegalArgumentException("GA cannot be negative");
        if (played != won + drawn + lost) {
            throw new IllegalArgumentException("Played must equal won+drawn+lost");
        }
        if (gd != gf - ga) {
            throw new IllegalArgumentException("GD must equal GF - GA");
        }
        this.played = played;
        this.won = won;
        this.drawn = drawn;
        this.lost = lost;
        this.points = points;
        this.gf = gf;
        this.ga = ga;
        this.gd = gd;
    }

    @Override
    public String toString() {
        return String.format("P%d W%d D%d L%d GF%d GA%d GD%+d Pts%d", played, won, drawn, lost, gf, ga, gd, points);
    }
}
