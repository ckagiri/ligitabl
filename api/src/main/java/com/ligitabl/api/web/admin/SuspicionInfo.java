package com.ligitabl.api.web.admin;

import java.util.List;

import com.ligitabl.model.domain.SuspicionTier;
import com.ligitabl.model.domain.SuspiciousEmailDetector;

public record SuspicionInfo(int score, SuspicionTier tier, List<String> reasons) {

    public String icon() {
        return switch (tier) {
            case FLAG -> "🚩";
            case CAUTION -> "⚠️";
            case NONE -> "";
        };
    }

    public String reasonsTooltip() {
        return reasons.isEmpty() ? "No suspicious patterns detected" : String.join("; ", reasons);
    }
}
