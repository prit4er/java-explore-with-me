package ru.practicum.stats;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import ru.practicum.HitRequest;
import ru.practicum.ViewStats;
import ru.practicum.mappers.HitMapper;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class StatsServiceImpl implements StatsService {

    private final HitRepository hitRepository;

    @Autowired
    public StatsServiceImpl(HitRepository hitRepository) {
        this.hitRepository = hitRepository;
    }

    @Override
    public void hit(HitRequest hitDto) {
        hitRepository.save(HitMapper.toEntity(hitDto));
    }

    @Override
    public List<ViewStats> stats(LocalDateTime start, LocalDateTime end,
                                 List<String> uris, boolean unique) {
        List<Object[]> results = unique ?
                                 hitRepository.findUniqueStats(start, end, uris) :
                                 hitRepository.findAllStats(start, end, uris);

        return results.stream()
                      .map(r -> new ViewStats(
                              (String) r[0],
                              (String) r[1],
                              ((Number) r[2]).longValue()
                      ))
                      .toList();
    }
}
