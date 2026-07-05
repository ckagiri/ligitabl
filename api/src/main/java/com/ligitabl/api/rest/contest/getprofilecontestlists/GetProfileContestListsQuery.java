package com.ligitabl.api.rest.contest.getprofilecontestlists;

import java.util.UUID;

public record GetProfileContestListsQuery(UUID userId, int activePage, int pastPage) {}
