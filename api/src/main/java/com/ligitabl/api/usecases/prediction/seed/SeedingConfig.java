package com.ligitabl.api.usecases.prediction.seed;

import java.util.List;

import lombok.Data;

// application/seeding/SeedingConfig.java
@Data
public class SeedingConfig {
    private String competitionSlug;
    private String seasonSlug;
    private int finishedRounds;
    private List<DemoUser> demoUsers;

    @Data
    public static class DemoUser {
        private String email;
        private String displayName;
    }
}
