package com.ligitabl.api.usecase.matchadmin.getmatchadmindetails;

public record GetMatchAdminDetailsQuery(String competitionIdentifier, Integer roundPosition, String matchSlug) {}
