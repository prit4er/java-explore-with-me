package ru.practicum.stats;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDateTime;
import java.util.List;

public interface HitRepository extends JpaRepository<Hit, Long> {

    @Query("SELECT h.app, h.uri, COUNT(DISTINCT h.ip) " +
            "FROM Hit h " +
            "WHERE h.timestamp BETWEEN ?1 AND ?2 " +
            "AND (?3 IS NULL OR h.uri IN ?3) " +
            "GROUP BY h.app, h.uri " +
            "ORDER BY COUNT(DISTINCT h.ip) DESC")
    List<Object[]> findUniqueStats(LocalDateTime start, LocalDateTime end, List<String> uris);

    @Query("SELECT h.app, h.uri, COUNT(h.id) " +
            "FROM Hit h " +
            "WHERE h.timestamp BETWEEN ?1 AND ?2 " +
            "AND (?3 IS NULL OR h.uri IN ?3) " +
            "GROUP BY h.app, h.uri " +
            "ORDER BY COUNT(h.id) DESC")
    List<Object[]> findAllStats(LocalDateTime start, LocalDateTime end, List<String> uris);
}
