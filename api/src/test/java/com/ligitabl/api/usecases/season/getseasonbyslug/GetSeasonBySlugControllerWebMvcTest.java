package com.ligitabl.api.usecases.season.getseasonbyslug;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.util.UUID;

import com.ligitabl.api.shared.Either;
import com.ligitabl.api.shared.errors.NotFoundError;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import com.ligitabl.api.usecases.season.SeasonDto;

@WebMvcTest(GetSeasonBySlugController.class)
class GetSeasonBySlugControllerWebMvcTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private GetSeasonBySlugUseCase getSeasonBySlugUseCase;

    @Test
    void shouldReturnSeasonWhenFound() throws Exception {
        var dto = SeasonDto.builder()
                .id(UUID.randomUUID())
                .competitionId(UUID.randomUUID())
                .name("2024/2025")
                .slug("2024-25")
                .startDate(LocalDate.of(2024, 8, 1))
                .endDate(LocalDate.of(2025, 5, 31))
                .maxRounds(38)
                .build();

        given(getSeasonBySlugUseCase.execute(any())).willReturn(Either.right(dto));

        mockMvc.perform(get("/api/competitions/premier-league/seasons/2024-25").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("2024/2025"))
                .andExpect(jsonPath("$.slug").value("2024-25"))
                .andExpect(jsonPath("$.maxRounds").value(38));
    }

    @Test
    void shouldReturnNotFoundWhenSeasonOrCompetitionMissing() throws Exception {
        given(getSeasonBySlugUseCase.execute(any()))
                .willReturn(Either.left(new NotFoundError("Season not found")));

        mockMvc.perform(get("/api/competitions/premier-league/seasons/unknown").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound());
    }
}
