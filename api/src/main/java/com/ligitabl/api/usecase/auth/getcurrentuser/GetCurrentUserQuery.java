package com.ligitabl.api.usecase.auth.getcurrentuser;

import com.ligitabl.model.domain.identity.PublicId;

public record GetCurrentUserQuery(PublicId publicId) {}
