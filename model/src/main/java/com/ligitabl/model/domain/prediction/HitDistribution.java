package com.ligitabl.model.domain.prediction;

public record HitDistribution(int perfect, int closeCalls, int nearMisses, int bigMisses) {}
