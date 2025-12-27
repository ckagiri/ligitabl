package com.ligitabl.api.usecases.prediction.seeding;

import lombok.Data;

import java.util.List;

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
