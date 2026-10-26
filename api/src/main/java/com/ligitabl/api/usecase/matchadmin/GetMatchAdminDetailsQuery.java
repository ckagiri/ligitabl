package com.ligitabl.api.usecase.matchadmin;

public record GetMatchAdminDetailsQuery(String competitionIdentifier, Integer roundPosition, String matchSlug) {}
