package com.ligitabl.model.domain;

import java.util.List;

import com.ligitabl.model.SwapChange;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class RoundSwap {
    private int round;
    private List<SwapChange> changes;
}
