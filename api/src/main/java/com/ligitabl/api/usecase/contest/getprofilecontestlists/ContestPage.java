package com.ligitabl.api.usecase.contest.getprofilecontestlists;

import java.util.List;

/** One page of a contest list. {@code from}/{@code to} are 1-based row numbers, 0 when empty. */
public record ContestPage(List<ContestSummary> contests, int total, int pages, int from, int to) {

    static ContestPage of(List<ContestSummary> contests, int total, int offset, int pageSize) {
        return new ContestPage(
                contests,
                total,
                (int) Math.ceil((double) total / pageSize),
                total == 0 ? 0 : offset + 1,
                Math.min(offset + pageSize, total));
    }
}
