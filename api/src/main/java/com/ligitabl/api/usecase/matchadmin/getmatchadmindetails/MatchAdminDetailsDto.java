package com.ligitabl.api.usecase.matchadmin.getmatchadmindetails;

import java.util.List;
import java.util.UUID;

import com.ligitabl.model.domain.league.MatchStatus;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class MatchAdminDetailsDto {
    UUID matchId;
    String matchSlug;
    MatchStatus status;
    int roundPosition;
    List<String> availableActions;
}
