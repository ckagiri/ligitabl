package com.ligitabl.api.web.leaderboard;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class UserDetailModalRequest {
    @NotBlank(message = "publicId is required")
    private String publicId;

    @NotBlank(message = "displayName is required")
    private String displayName;

    @NotNull(message = "position is required")
    private Integer position;

    @NotNull(message = "totalScore is required")
    private Integer totalScore;

    @NotNull(message = "roundScore is required")
    private Integer roundScore;

    @NotNull(message = "totalZeroes is required")
    private Integer totalZeroes;

    @NotNull(message = "totalSwaps is required")
    private Integer totalSwaps;

    @NotNull(message = "movement is required")
    private Integer movement;
}
