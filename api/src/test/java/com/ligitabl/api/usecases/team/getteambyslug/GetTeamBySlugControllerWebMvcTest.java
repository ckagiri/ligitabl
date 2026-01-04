package com.ligitabl.api.usecases.team.getteambyslug;

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
import com.ligitabl.api.usecases.team.TeamDto;

@WebMvcTest(controllers = GetTeamBySlugController.class)
class GetTeamBySlugControllerWebMvcTest {

    @Autowired
    private MockMvc mockMvc;

    @SuppressWarnings("removal")
    @MockBean
    private GetTeamBySlugUseCase getTeamBySlugUseCase;

    @Test
    @DisplayName("GET /api/teams/{slug} -> 200 OK with body")
    void getBySlug_success() throws Exception {
        var id = UUID.randomUUID();
        var slug = "arsenal";
        var dto = TeamDto.builder()
                .id(id)
                .name("Arsenal FC")
                .shortName("Arsenal")
                .slug(slug)
                .tla("ARS")
                .build();

        when(getTeamBySlugUseCase.execute(any())).thenReturn(Either.right(dto));

        mockMvc.perform(get("/api/teams/{slug}", slug))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id", equalTo(id.toString())))
                .andExpect(jsonPath("$.slug", equalTo(slug)));
    }

    @Test
    @DisplayName("GET /api/teams/{slug} -> 404 Not Found mapped from business error")
    void getBySlug_notFound() throws Exception {
        var slug = "unknown";
        var error = UseCaseErrors.notFound("Team", "slug", slug);
        when(getTeamBySlugUseCase.execute(any())).thenReturn(Either.left(error));

        mockMvc.perform(get("/api/teams/{slug}", slug))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error", equalTo("Not Found")))
                .andExpect(jsonPath("$.message", equalTo(error.getMessage())))
                .andExpect(jsonPath("$.status", equalTo("NOT_FOUND")));
    }

    @Test
    @DisplayName("GET /api/teams/{slug} -> 400 Bad Request mapped from validation error")
    void getBySlug_validationError() throws Exception {
        var slug = "Invalid Slug"; // will be normalized/validated in use case; simulate validation failure
        var error = UseCaseErrors.validation(
                "slug", "Only lowercase letters, digits, and hyphens. No spaces, no uppercase");
        when(getTeamBySlugUseCase.execute(any())).thenReturn(Either.left(error));

        mockMvc.perform(get("/api/teams/{slug}", slug))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error", equalTo("Validation Failed")))
                .andExpect(jsonPath("$.message", equalTo(error.getMessage())))
                .andExpect(jsonPath("$.status", equalTo("BAD_REQUEST")));
    }
}
