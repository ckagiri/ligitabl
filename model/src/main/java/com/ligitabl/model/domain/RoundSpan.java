package com.ligitabl.model.domain;

import lombok.Builder;
import lombok.Value;
import lombok.extern.jackson.Jacksonized;

@Value
@Builder
@Jacksonized
public class RoundSpan {
    int from;
    int to;
}
