package com.ligitabl.model.domain;

import lombok.Builder;
import lombok.Value;
import lombok.extern.jackson.Jacksonized;

@Value
@Builder
@Jacksonized
public class RoundSpan {
    String code;
    String name;
    int from;
    int to;
    PhaseType type;
}
