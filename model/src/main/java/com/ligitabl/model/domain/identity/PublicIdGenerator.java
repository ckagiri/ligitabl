package com.ligitabl.model.domain.identity;

import java.util.UUID;

public interface PublicIdGenerator {
    PublicId generate(UUID uuid);
}
