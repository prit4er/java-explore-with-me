package ru.practicum.stats;

import org.springframework.stereotype.Service;
import ru.practicum.HitRequest;
import ru.practicum.ViewStats;

import java.time.LocalDateTime;
import java.util.List;

@Service
public interface StatsService {

    void hit(HitRequest hitDto);

    List<ViewStats> stats(LocalDateTime start, LocalDateTime end, List<String> uris, boolean unique);

}
