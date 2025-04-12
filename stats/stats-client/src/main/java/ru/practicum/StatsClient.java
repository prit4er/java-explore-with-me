package ru.practicum;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

import static org.springframework.http.MediaType.APPLICATION_JSON;

@Slf4j
@Component
public class StatsClient {

    private final RestClient restClient;
    private final String statsUrl;

    public StatsClient(@Value("${stats-server.url}") String statsUrl) {
        this.statsUrl = statsUrl;
        this.restClient = RestClient.create(statsUrl);
    }

    public void hit(HitRequest hitDto) {
        String url = UriComponentsBuilder.fromHttpUrl(statsUrl).path("/hit").toUriString();
        ResponseEntity<Void> response = restClient.post()
                                                  .uri(url)
                                                  .contentType(APPLICATION_JSON)
                                                  .body(hitDto)
                                                  .retrieve()
                                                  .toBodilessEntity();
    }

    public List<ViewStats> getStats(LocalDateTime start, LocalDateTime end, List<String> uris, Boolean unique) {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");

        String formattedStart = start.format(formatter);
        String formattedEnd = end.format(formatter);

        UriComponentsBuilder builder = UriComponentsBuilder.fromHttpUrl(statsUrl)
                                                           .path("/stats")
                                                           .queryParam("start", formattedStart)
                                                           .queryParam("end", formattedEnd)
                                                           .queryParam("unique", unique);

        if (uris != null && !uris.isEmpty()) {
            builder.queryParam("uris", String.join(",", uris));
        }

        String url = builder.toUriString();

        try {
            return restClient.get()
                             .uri(url)
                             .retrieve()
                             .body(new ParameterizedTypeReference<>() {
                             });
        } catch (HttpClientErrorException e) {
            log.error("Error getting stats from stats-server. URL: {}, Response: {}", url, e.getResponseBodyAsString());
            throw e;
        }
    }
}
