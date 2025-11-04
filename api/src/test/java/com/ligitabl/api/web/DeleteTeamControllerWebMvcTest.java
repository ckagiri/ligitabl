package com.ligitabl.api.web;

import static org.hamcrest.Matchers.equalTo;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
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
import com.ligitabl.api.shared.Unit;
import com.ligitabl.api.shared.errors.UseCaseErrors;
import com.ligitabl.api.usecases.team.deleteteam.DeleteTeamController;
import com.ligitabl.api.usecases.team.deleteteam.DeleteTeamUseCase;

@WebMvcTest(controllers = DeleteTeamController.class)
class DeleteTeamControllerWebMvcTest {

    @Autowired
    private MockMvc mockMvc;

    @SuppressWarnings("removal")
    @MockBean
    private DeleteTeamUseCase deleteTeamUseCase;

    @Test
    @DisplayName("DELETE /api/teams/{id} -> 204 No Content")
    void deleteTeam_success() throws Exception {
        when(deleteTeamUseCase.execute(any())).thenReturn(Either.right(Unit.INSTANCE));

        mockMvc.perform(delete("/api/teams/{id}", UUID.randomUUID())).andExpect(status().isNoContent());
    }

    @Test
    @DisplayName("DELETE /api/teams/{id} -> 404 Not Found mapped from business error")
    void deleteTeam_notFound() throws Exception {
        var id = UUID.randomUUID().toString();
        var error = UseCaseErrors.notFound("Team", id);
        when(deleteTeamUseCase.execute(any())).thenReturn(Either.left(error));

        mockMvc.perform(delete("/api/teams/{id}", id))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error", equalTo("Not Found")))
                .andExpect(jsonPath("$.message", equalTo(error.getMessage())))
                .andExpect(jsonPath("$.status", equalTo("NOT_FOUND")));
    }

    @Test
    @DisplayName("DELETE /api/teams/{id} -> 400 Bad Request mapped from validation error")
    void deleteTeam_validationError() throws Exception {
        var id = "not-a-uuid";
        var error = UseCaseErrors.validation("id", "must be a valid UUID");
        when(deleteTeamUseCase.execute(any())).thenReturn(Either.left(error));

        mockMvc.perform(delete("/api/teams/{id}", id))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error", equalTo("Validation Failed")))
                .andExpect(jsonPath("$.message", equalTo(error.getMessage())))
                .andExpect(jsonPath("$.status", equalTo("BAD_REQUEST")));
    }
}
