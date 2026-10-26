package com.ligitabl.api.usecase.matchadmin.updatekickoff;

import java.time.OffsetDateTime;

public record UpdateMatchKickoffCommand(Integer roundPosition, String matchSlug, OffsetDateTime newKickOff) {}
