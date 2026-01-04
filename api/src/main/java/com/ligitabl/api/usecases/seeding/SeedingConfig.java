package com.ligitabl.api.usecases.seeding;

import java.util.List;

import lombok.Data;

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
