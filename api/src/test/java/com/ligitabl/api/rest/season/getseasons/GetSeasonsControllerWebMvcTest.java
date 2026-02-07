package com.ligitabl.api.rest.season.getseasons;

import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import com.ligitabl.api.rest.season.SeasonDto;
import com.ligitabl.api.shared.Either;

@WebMvcTest(GetSeasonsController.class)
class GetSeasonsControllerWebMvcTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private GetSeasonsUseCase getSeasonsUseCase;

    @Test
    void shouldReturnSeasons() throws Exception {
        var dto = SeasonDto.builder()
                .id(UUID.randomUUID())
                .competitionId(UUID.randomUUID())
                .name("2024/25 Premier League")
                .slug("2024-25-premier-league")
                .startDate(LocalDate.of(2024, 8, 1))
                .endDate(LocalDate.of(2025, 5, 31))
                .maxRounds(38)
                .build();

        given(getSeasonsUseCase.execute(new GetSeasonsQuery("premier-league"))).willReturn(Either.right(List.of(dto)));

        mockMvc.perform(get("/api/competitions/{competitionSlug}/seasons", "premier-league")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("2024/25 Premier League"))
                .andExpect(jsonPath("$[0].slug").value("2024-25-premier-league"))
                .andExpect(jsonPath("$[0].maxRounds").value(38));
    }
}
