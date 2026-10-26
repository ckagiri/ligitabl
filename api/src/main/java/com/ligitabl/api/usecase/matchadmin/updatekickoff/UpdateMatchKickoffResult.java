package com.ligitabl.api.usecase.matchadmin.updatekickoff;

import java.time.OffsetDateTime;
import java.util.UUID;

public record UpdateMatchKickoffResult(UUID matchId, String matchSlug, OffsetDateTime newKickOff) {}
