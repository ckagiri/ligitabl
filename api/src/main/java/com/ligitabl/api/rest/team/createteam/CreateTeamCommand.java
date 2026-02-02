package com.ligitabl.api.rest.team.createteam;

import com.ligitabl.api.rest.team.TeamPayload;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

@Getter
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
@SuperBuilder
public class CreateTeamCommand extends TeamPayload {}
