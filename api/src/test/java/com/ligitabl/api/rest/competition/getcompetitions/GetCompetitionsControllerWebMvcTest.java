package com.ligitabl.api.rest.competition.getcompetitions;

import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import com.ligitabl.api.rest.competition.CompetitionDto;
import com.ligitabl.api.shared.Either;

@WebMvcTest(GetCompetitionsController.class)
class GetCompetitionsControllerWebMvcTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private GetCompetitionsUseCase getCompetitionsUseCase;

    @Test
    void shouldReturnCompetitions() throws Exception {
        var dto = CompetitionDto.builder()
                .id(UUID.randomUUID())
                .name("Premier League")
                .slug("premier-league")
                .code("PL")
                .build();

        given(getCompetitionsUseCase.execute()).willReturn(Either.right(List.of(dto)));

        mockMvc.perform(get("/api/competitions").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("Premier League"))
                .andExpect(jsonPath("$[0].slug").value("premier-league"))
                .andExpect(jsonPath("$[0].code").value("PL"));
    }
}
