package com.ligitabl.model.domain;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class RoundSpan {
    int from;
    int to;
}
