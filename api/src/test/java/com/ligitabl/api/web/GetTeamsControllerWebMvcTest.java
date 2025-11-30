package com.ligitabl.api.web;

import static org.hamcrest.Matchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import com.ligitabl.api.shared.Either;
import com.ligitabl.api.usecases.team.TeamDto;
import com.ligitabl.api.usecases.team.getteams.GetTeamsController;
import com.ligitabl.api.usecases.team.getteams.GetTeamsUseCase;

@WebMvcTest(controllers = GetTeamsController.class)
class GetTeamsControllerWebMvcTest {

    @Autowired
    private MockMvc mockMvc;

    @SuppressWarnings("removal")
    @MockBean
    private GetTeamsUseCase getTeamsUseCase;

    @Test
    @DisplayName("GET /api/teams -> 200 OK with list of teams")
    void listTeams_success() throws Exception {
        var t1 = TeamDto.builder()
                .id(UUID.randomUUID())
                .name("Arsenal FC")
                .shortName("Arsenal")
                .slug("arsenal")
                .tla("ARS")
                .build();
        var t2 = TeamDto.builder()
                .id(UUID.randomUUID())
                .name("Chelsea FC")
                .shortName("Chelsea")
                .slug("chelsea")
                .tla("CHE")
                .build();

        when(getTeamsUseCase.execute()).thenReturn(Either.right(List.of(t1, t2)));

        mockMvc.perform(get("/api/teams"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].slug", equalTo("arsenal")))
                .andExpect(jsonPath("$[1].slug", equalTo("chelsea")));
    }
}
