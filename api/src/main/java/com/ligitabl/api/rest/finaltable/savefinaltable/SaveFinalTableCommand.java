package com.ligitabl.api.rest.finaltable.savefinaltable;

import java.util.List;

/**
 * A batch of swaps the client has already applied locally, in the order it applied them, plus the
 * order it expects to end up with.
 *
 * @param swaps the pairs tapped since the last save; empty is legal only on the very first save
 * @param expectedOrder team codes in final position order — a checksum, never the stored payload
 */
public record SaveFinalTableCommand(List<SwapPair> swaps, List<String> expectedOrder) {

    public record SwapPair(String teamA, String teamB) {}

    public List<SwapPair> safeSwaps() {
        return swaps == null ? List.of() : swaps;
    }

    public List<String> safeExpectedOrder() {
        return expectedOrder == null ? List.of() : expectedOrder;
    }

    public boolean isEmptyBatch() {
        return safeSwaps().isEmpty();
    }
}
