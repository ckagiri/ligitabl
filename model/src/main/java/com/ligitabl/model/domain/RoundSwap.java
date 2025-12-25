package com.ligitabl.model.domain;

import com.ligitabl.model.SwapChange;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class RoundSwap {
    private int round;
    private List<SwapChange> changes;
}
