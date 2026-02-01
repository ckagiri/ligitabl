package com.ligitabl.api.usecases.prediction.createprediction;

public record TeamRankDto(String code, int position) {
    public static TeamRankDto of(String code, int position) {
        return new TeamRankDto(code, position);
    }
}
