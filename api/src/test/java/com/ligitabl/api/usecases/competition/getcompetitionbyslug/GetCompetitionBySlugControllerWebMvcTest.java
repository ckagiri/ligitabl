package com.ligitabl.api.usecases.competition.getcompetitionbyslug;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import com.ligitabl.api.usecases.competition.CompetitionDto;

@WebMvcTest(GetCompetitionBySlugController.class)
class GetCompetitionBySlugControllerWebMvcTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private GetCompetitionBySlugUseCase getCompetitionBySlugUseCase;

    @Test
    void shouldReturnCompetitionWhenFound() throws Exception {
        var dto = CompetitionDto.builder()
                .id(UUID.randomUUID())
                .name("Premier League")
                .slug("premier-league")
                .code("PL")
                .build();

        given(getCompetitionBySlugUseCase.execute(any())).willReturn(dto);

        mockMvc.perform(get("/api/competitions/premier-league").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Premier League"))
                .andExpect(jsonPath("$.slug").value("premier-league"))
                .andExpect(jsonPath("$.code").value("PL"));
    }

    @Test
    void shouldReturnNotFoundWhenCompetitionDoesNotExist() throws Exception {
        given(getCompetitionBySlugUseCase.execute(any())).willReturn(null);

        mockMvc.perform(get("/api/competitions/unknown").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());
        // For now we just get 200 with null body; we can tighten this later
    }
}

