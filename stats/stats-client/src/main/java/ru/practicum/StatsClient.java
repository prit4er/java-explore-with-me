package ru.practicum;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;


@Component
public class StatsClient {

    private final RestClient restClient;
    private final String baseUrl;

    public StatsClient(@Value("${stats-server.url}") String baseUrl) {
        this.baseUrl = baseUrl;
        this.restClient = RestClient.builder()
                                    .baseUrl(baseUrl)
                                    .defaultHeader("Content-Type", "application/json")
                                    .build();
    }

    public void hit(HitRequest hitDto) {
        restClient.post()
                  .uri("/hit")
                  .body(hitDto)
                  .retrieve()
                  .toBodilessEntity();
    }

    public List<ViewStats> stats(LocalDateTime start,
                                 LocalDateTime end,
                                 List<String> uris,
                                 Boolean unique) {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

        return restClient.get()
                         .uri(uriBuilder -> uriBuilder.path("/stats")
                                                      .queryParam("start", start.format(formatter))
                                                      .queryParam("end", end.format(formatter))
                                                      .queryParamIfPresent("uris", Optional.ofNullable(uris))
                                                      .queryParam("unique", unique)
                                                      .build())
                         .retrieve()
                         .body(new ParameterizedTypeReference<>() {
                         });
    }
}
