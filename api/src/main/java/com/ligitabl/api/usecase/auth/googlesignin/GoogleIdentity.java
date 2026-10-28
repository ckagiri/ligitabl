package com.ligitabl.api.usecase.auth.googlesignin;

/** What Google vouched for. The adapter has already checked that subject and email are present. */
public record GoogleIdentity(String subject, String email, String name) {}
