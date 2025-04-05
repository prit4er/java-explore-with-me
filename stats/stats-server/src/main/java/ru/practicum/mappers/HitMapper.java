package ru.practicum.mappers;

import org.springframework.stereotype.Component;
import ru.practicum.HitRequest;
import ru.practicum.stats.Hit;

import java.time.LocalDateTime;

@Component
public class HitMapper {

    public static Hit toEntity(HitRequest hitDto) {
        return new Hit(
                null,
                hitDto.getApp(),
                hitDto.getUri(),
                hitDto.getIp(),
                LocalDateTime.now()
        );
    }
}
