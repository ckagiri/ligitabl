package com.ligitabl.api.usecase.team.createteam;

import com.ligitabl.api.usecase.team.shared.TeamPayload;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

@Getter
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
@SuperBuilder
public class CreateTeamCommand extends TeamPayload {}
