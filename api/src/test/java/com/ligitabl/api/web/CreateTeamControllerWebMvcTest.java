package com.ligitabl.api.web;

import static org.hamcrest.Matchers.equalTo;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
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
import com.ligitabl.api.usecases.team.createteam.CreateTeamController;
import com.ligitabl.api.usecases.team.createteam.CreateTeamPort;

@WebMvcTest(controllers = CreateTeamController.class)
class CreateTeamControllerWebMvcTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    // TODO: Switch to Spring Framework @MockitoBean when we finalize the migration; keeping @MockBean for stability.
    @SuppressWarnings("removal")
    @MockBean
    private CreateTeamPort createTeamUseCase;

    @Test
    @DisplayName("POST /api/teams -> 201 Created with Location and body")
    void createTeam_success() throws Exception {
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

        when(createTeamUseCase.execute(any())).thenReturn(Either.right(dto));

        var payload = new java.util.HashMap<String, Object>();
        payload.put("name", "Arsenal FC");
        payload.put("shortName", "Arsenal");
        payload.put("slug", slug);
        payload.put("tla", "ARS");

        // Act + Assert
        mockMvc.perform(post("/api/teams")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/api/teams/" + id))
                .andExpect(jsonPath("$.id", equalTo(id.toString())))
                .andExpect(jsonPath("$.slug", equalTo(slug)));
    }

    @Test
    @DisplayName("POST /api/teams -> 409 Conflict mapped from business error")
    void createTeam_conflict() throws Exception {
        // Arrange
        var error = UseCaseErrors.conflict("Team with slug arsenal already exists");
        when(createTeamUseCase.execute(any())).thenReturn(Either.left(error));

        var payload = new java.util.HashMap<String, Object>();
        payload.put("name", "Arsenal FC");
        payload.put("shortName", "Arsenal");
        payload.put("slug", "arsenal");
        payload.put("tla", "ARS");

        // Act + Assert
        mockMvc.perform(post("/api/teams")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error", equalTo("Business Rule Violation")))
                .andExpect(jsonPath("$.message", equalTo(error.getMessage())))
                .andExpect(jsonPath("$.code", equalTo(409)));
    }
}
