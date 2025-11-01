package com.ligitabl.api.web;

import static org.hamcrest.Matchers.equalTo;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ligitabl.api.shared.Either;
import com.ligitabl.api.shared.errors.UseCaseErrors;
import com.ligitabl.api.usecases.team.TeamDto;
import com.ligitabl.api.usecases.team.TeamPayload;
import com.ligitabl.api.usecases.team.updateteam.UpdateTeamController;
import com.ligitabl.api.usecases.team.updateteam.UpdateTeamPort;

@WebMvcTest(controllers = UpdateTeamController.class)
class UpdateTeamControllerWebMvcTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    // TODO: Switch to Spring Framework @MockitoBean when we finalize the migration; keeping @MockBean for stability.
    @SuppressWarnings("removal")
    @MockBean
    private UpdateTeamPort updateTeamUseCase;

    @Test
    @DisplayName("PUT /api/teams/{id} -> 200 OK with body")
    void updateTeam_success() throws Exception {
        // Arrange
        UUID id = UUID.randomUUID();
        String slug = "arsenal";
        var dto = TeamDto.builder()
                .id(id)
                .name("Arsenal FC")
                .shortName("Arsenal")
                .slug(slug)
                .tla("ARS")
                .build();

        when(updateTeamUseCase.execute(any())).thenReturn(Either.right(dto));

        var payload = TeamPayload.builder()
                .name("Arsenal FC")
                .shortName("Arsenal")
                .slug(slug)
                .tla("ARS")
                .build();

        // Act + Assert
        mockMvc.perform(put("/api/teams/{id}", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id", equalTo(id.toString())))
                .andExpect(jsonPath("$.slug", equalTo(slug)));
    }

    @Test
    @DisplayName("PUT /api/teams/{id} -> 404 Not Found mapped from business error")
    void updateTeam_notFound() throws Exception {
        // Arrange
        UUID id = UUID.randomUUID();
        var error = UseCaseErrors.notFound("Team", id);
        when(updateTeamUseCase.execute(any())).thenReturn(Either.left(error));

        var payload = TeamPayload.builder()
                .name("Arsenal FC")
                .shortName("Arsenal")
                .slug("arsenal")
                .tla("ARS")
                .build();

        // Act + Assert
        mockMvc.perform(put("/api/teams/{id}", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error", equalTo("Not Found")))
                .andExpect(jsonPath("$.message", equalTo(error.getMessage())))
                .andExpect(jsonPath("$.status", equalTo("NOT_FOUND")));
    }
}
