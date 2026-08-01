package com.ligitabl.api.web.shared.swap;

import java.util.Comparator;
import java.util.List;

import org.springframework.stereotype.Component;

import com.ligitabl.model.domain.SwapChange;

/**
 * Turns the stored {@code "CODE:from→to"} swap encoding into the fields the "Swap history" fragment
 * renders. Shared by the owner's own table and the public shared view, so both parse the encoding
 * the same way.
 */
@Component
public class SwapHistoryFormatter {

    /** A single swap, as displayed: "ARS #1 → #4 ↔ CHE #4 → #1". */
    public record Entry(
            String teamACode,
            int teamAFrom,
            int teamATo,
            String teamBCode,
            int teamBFrom,
            int teamBTo,
            String formattedTime) {}

    public List<Entry> format(List<SwapChange> changes) {
        if (changes == null || changes.isEmpty()) {
            return List.of();
        }
        return changes.stream()
                .sorted(Comparator.comparing(SwapChange::timestamp))
                .map(swap -> {
                    String[] partsA = swap.teamA().split(":");
                    String[] posA = partsA[1].split("→"); // →
                    String[] partsB = swap.teamB().split(":");
                    String[] posB = partsB[1].split("→"); // →
                    return new Entry(
                            partsA[0],
                            Integer.parseInt(posA[0]),
                            Integer.parseInt(posA[1]),
                            partsB[0],
                            Integer.parseInt(posB[0]),
                            Integer.parseInt(posB[1]),
                            swap.timestamp().toString()); // ISO 8601 UTC — formatted client-side
                })
                .toList();
    }
}
