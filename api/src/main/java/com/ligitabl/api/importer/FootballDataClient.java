package com.ligitabl.api.importer;

import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Thin HTTP client for football-data.org endpoints we use in the
 * workflow (competitions and matches).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class FootballDataClient {

    private final FootballDataProperties properties;

    private final RestTemplate restTemplate = new RestTemplate();

    public ExternalCompetitionDto fetchCompetition(String competitionCode) {
        String url = String.format("%s/competitions/%s", properties.getBaseUrl(), competitionCode);
        log.info("Fetching competition from external API: {}", url);

        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Auth-Token", properties.getApiKey());

        HttpEntity<Void> entity = new HttpEntity<>(headers);

        ResponseEntity<ExternalCompetitionDto> response =
                restTemplate.exchange(url, HttpMethod.GET, entity, ExternalCompetitionDto.class);

        return response.getBody();
    }

    public ExternalMatchDto.MatchesResponse fetchMatchesForCompetition(String competitionCode) {
        String url = String.format("%s/competitions/%s/matches", properties.getBaseUrl(), competitionCode);
        log.info("Fetching matches from external API: {}", url);

        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Auth-Token", properties.getApiKey());

        HttpEntity<Void> entity = new HttpEntity<>(headers);

        ResponseEntity<ExternalMatchDto.MatchesResponse> response = restTemplate.exchange(
                url, HttpMethod.GET, entity, new ParameterizedTypeReference<ExternalMatchDto.MatchesResponse>() {});

        return response.getBody();
    }
}
