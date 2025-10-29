package com.ligitabl.api.usecases.team.createteam;

import com.ligitabl.api.usecases.team.TeamPayload;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

@Getter
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
@SuperBuilder
public class CreateTeamCommand extends TeamPayload {}
