package com.ligitabl.seed.internal.scoring;

import java.util.UUID;

public class TeamProfile {
    private final UUID id;
    private final String name;
    private final String code;
    private final TeamStrength category;
    private final int rating;

    public TeamProfile(UUID id, String name, String code, int rating) {
        this.id = id;
        this.name = name;
        this.code = code;
        this.rating = Math.max(0, Math.min(100, rating));
        this.category = TeamStrength.fromRating(this.rating);
    }

    public double getAttackingStrength() {
        return 0.5 + (rating / 100.0);
    }

    public double getDefensiveStrength() {
        return (rating / 100.0) * 0.5;
    }

    public UUID getId() { return id; }
    public String getName() { return name; }
    public String getCode() { return code; }
    public TeamStrength getCategory() { return category; }
    public int getRating() { return rating; }
}
