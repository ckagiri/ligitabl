package com.ligitabl.api.usecase.prediction.latestresult;

/** {@code pending}: the user has a latest result they haven't dismissed yet. */
public record GetResultBannerSettingResult(boolean resultPresent, boolean pending) {}
