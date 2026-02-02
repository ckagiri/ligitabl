package com.ligitabl.api.rest.team.getteambyid;

import static org.hamcrest.Matchers.equalTo;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import com.ligitabl.api.shared.Either;
import com.ligitabl.api.shared.errors.UseCaseErrors;
import com.ligitabl.api.rest.team.TeamDto;

@WebMvcTest(controllers = GetTeamByIdController.class)
class GetTeamByIdControllerWebMvcTest {

    @Autowired
    private MockMvc mockMvc;

    @SuppressWarnings("removal")
    @MockBean
    private GetTeamByIdUseCase getTeamByIdUseCase;

    @Test
    @DisplayName("GET /api/teams?id={uuid} -> 200 OK with body")
    void getById_success() throws Exception {
        var id = UUID.randomUUID();
        var dto = TeamDto.builder()
                .id(id)
                .name("Arsenal FC")
                .shortName("Arsenal")
                .slug("arsenal")
                .tla("ARS")
                .build();

        when(getTeamByIdUseCase.execute(any())).thenReturn(Either.right(dto));

        mockMvc.perform(get("/api/teams").param("id", id.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id", equalTo(id.toString())))
                .andExpect(jsonPath("$.slug", equalTo("arsenal")));
    }

    @Test
    @DisplayName("GET /api/teams?id={uuid} -> 404 Not Found mapped from business error")
    void getById_notFound() throws Exception {
        var id = UUID.randomUUID().toString();
        var error = UseCaseErrors.notFound("Team", id);
        when(getTeamByIdUseCase.execute(any())).thenReturn(Either.left(error));

        mockMvc.perform(get("/api/teams").param("id", id))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error", equalTo("Not Found")))
                .andExpect(jsonPath("$.message", equalTo(error.getMessage())))
                .andExpect(jsonPath("$.status", equalTo("NOT_FOUND")));
    }

    @Test
    @DisplayName("GET /api/teams?id={uuid} -> 400 Bad Request mapped from validation error")
    void getById_validationError() throws Exception {
        var badId = "not-a-uuid";
        var error = UseCaseErrors.validation("id", "must be a valid UUID");
        when(getTeamByIdUseCase.execute(any())).thenReturn(Either.left(error));

        mockMvc.perform(get("/api/teams").param("id", badId))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error", equalTo("Validation Failed")))
                .andExpect(jsonPath("$.message", equalTo(error.getMessage())))
                .andExpect(jsonPath("$.status", equalTo("BAD_REQUEST")));
    }
}
