package ru.practicum.stats;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import ru.practicum.HitRequest;
import ru.practicum.ViewStats;
import ru.practicum.mappers.HitMapper;

import java.time.LocalDateTime;
import java.util.List;

@Component
public class StatsServiceImpl implements StatsService {

    private final HitRepository hitRepository;

    @Autowired
    public StatsServiceImpl(HitRepository hitRepository) {
        this.hitRepository = hitRepository;
    }

    @Override
    public void hit(HitRequest hitDto) {
        Hit hit = HitMapper.toEntity(hitDto);
        hitRepository.save(hit);
    }

    @Override
    public List<ViewStats> stats(LocalDateTime start, LocalDateTime end, List<String> uris, boolean unique) {
        if (unique) {
            return (uris == null) ? hitRepository.findUniqueHitsWithoutUri(start, end)
                                  : hitRepository.findUniqueHitsWithUri(start, end, uris);
        } else {
            return (uris == null) ? hitRepository.findHitsWithoutUri(start, end)
                                  : hitRepository.findHitsWithUri(start, end, uris);
        }
    }
}
