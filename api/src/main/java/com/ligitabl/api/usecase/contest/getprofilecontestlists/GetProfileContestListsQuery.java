package com.ligitabl.api.usecase.contest.getprofilecontestlists;

import java.util.UUID;

public record GetProfileContestListsQuery(UUID userId, int activePage, int pastPage) {}
