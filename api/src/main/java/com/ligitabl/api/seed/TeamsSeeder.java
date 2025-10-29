package com.ligitabl.api.seed;

import java.util.List;
import java.util.Locale;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ligitabl.model.domain.Team;
import com.ligitabl.model.repo.TeamRepo;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class TeamsSeeder {
    private final TeamRepo teamRepo;

    @Transactional
    public void seed(List<TeamSeedEntry> entries) {
        if (entries == null || entries.isEmpty()) {
            log.info("No team entries to seed");
            return;
        }

        for (TeamSeedEntry e : entries) {
            if (e == null) continue;

            final String slug = e.getSlug();
            // Normalize slug to lowercase for consistency
            final String normalizedSlug = slug == null ? null : slug.toLowerCase(Locale.ROOT);

            teamRepo.findBySlug(normalizedSlug)
                    .ifPresentOrElse(
                            existing -> {
                                // Update if any field changed
                                boolean changed = false;
                                if (!safeEq(existing.getName(), e.getName())) {
                                    existing.setName(e.getName());
                                    changed = true;
                                }
                                if (!safeEq(existing.getShortName(), e.getShortName())) {
                                    existing.setShortName(e.getShortName());
                                    changed = true;
                                }
                                if (!safeEq(existing.getTla(), e.getTla())) {
                                    existing.setTla(e.getTla());
                                    changed = true;
                                }
                                if (!safeEq(existing.getSlug(), normalizedSlug)) {
                                    existing.setSlug(normalizedSlug);
                                    changed = true;
                                }
                                if (changed) {
                                    teamRepo.update(existing);
                                    log.info("Updated team '{}' (slug={})", existing.getName(), normalizedSlug);
                                } else {
                                    log.debug("No changes for team slug={}", normalizedSlug);
                                }
                            },
                            () -> {
                                Team toCreate = Team.builder()
                                        .name(e.getName())
                                        .shortName(e.getShortName())
                                        .slug(normalizedSlug)
                                        .tla(e.getTla())
                                        .build();
                                Team created = teamRepo.create(toCreate);
                                log.info("Created team '{}' (slug={})", created.getName(), normalizedSlug);
                            });
        }
    }

    private static boolean safeEq(Object a, Object b) {
        return (a == b) || (a != null && a.equals(b));
    }
}
