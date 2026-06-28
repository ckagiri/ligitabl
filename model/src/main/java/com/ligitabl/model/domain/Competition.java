package com.ligitabl.model.domain;

import java.util.List;
import java.util.UUID;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.SuperBuilder;

@Data
@EqualsAndHashCode(callSuper = true)
@SuperBuilder
public class Competition extends AbstractModel<UUID> {
    @NotNull
    private String name;

    @NotBlank
    private CompetitionSlug slug;

    @NotNull
    private String code;

    private List<RoundSpan> phases;

    private UUID activeSeasonId;

    public RoundSpan sprintForRound(int round) {
        if (phases == null || phases.isEmpty()) {
            throw new IllegalStateException("No phases configured for competition");
        }
        var matches = phases.stream()
                .filter(p -> p.getType() == PhaseType.SPRINT)
                .filter(p -> round >= p.getFrom() && round <= p.getTo())
                .toList();
        if (matches.isEmpty()) {
            throw new IllegalStateException("Round " + round + " is not assigned to any sprint");
        }
        if (matches.size() > 1) {
            throw new IllegalStateException(
                    "Round " + round + " belongs to multiple sprints (configuration error)");
        }
        return matches.get(0);
    }
}
