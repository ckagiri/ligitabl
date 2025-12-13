package com.ligitabl.seed;

import com.ligitabl.seed.internal.CompetitionSeeder;
import com.ligitabl.seed.internal.DefaultsSeeder;
import com.ligitabl.seed.internal.RoundSeeder;
import com.ligitabl.seed.internal.SeasonSeeder;
import com.ligitabl.seed.internal.SeedLoader;
import com.ligitabl.seed.internal.SeedResult;
import java.util.List;
import java.util.Map;
import org.jooq.DSLContext;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

@SpringBootApplication
public class SeedingApplication {

    public static void main(String[] args) {
        SpringApplication.run(SeedingApplication.class, args);
    }

    @Bean
    CommandLineRunner seedingRunner(DSLContext dsl) {
        return args -> {
            SeedLoader loader = new SeedLoader();
            Map<String, Object> sections = loader.loadFromClasspath("seeding/main.yaml");

                @SuppressWarnings("unchecked")
                List<Map<String, Object>> competitions =
                    (List<Map<String, Object>>) sections.getOrDefault("competition", List.of());
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> seasons =
                    (List<Map<String, Object>>) sections.getOrDefault("season", List.of());
                Object roundConfig = sections.get("round");

            CompetitionSeeder competitionSeeder = new CompetitionSeeder(dsl);
            SeasonSeeder seasonSeeder = new SeasonSeeder(dsl);
            RoundSeeder roundSeeder = new RoundSeeder(dsl);
            DefaultsSeeder defaultsSeeder = new DefaultsSeeder(dsl);

            SeedResult competitionResult = competitionSeeder.seed(competitions);
            SeedResult seasonResult = seasonSeeder.seed(seasons);
            SeedResult roundResult = roundSeeder.seed(roundConfig);
            defaultsSeeder.applyDefaults();

            System.out.printf(
                    "[seed] competition inserted=%d skipped=%d%n",
                    competitionResult.inserted(), competitionResult.skipped());
            System.out.printf(
                    "[seed] season      inserted=%d skipped=%d%n",
                    seasonResult.inserted(), seasonResult.skipped());
            System.out.printf(
                    "[seed] round       inserted=%d skipped=%d%n",
                    roundResult.inserted(), roundResult.skipped());
        };
    }
}
