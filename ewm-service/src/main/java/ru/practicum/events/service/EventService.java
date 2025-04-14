package ru.practicum.events.service;

import jakarta.servlet.http.HttpServletRequest;
import ru.practicum.events.dto.EventDto;
import ru.practicum.events.dto.EventDtoWithViews;
import ru.practicum.events.dto.EventShortDto;
import ru.practicum.events.dto.EventShortDtoWithViews;
import ru.practicum.events.dto.NewEventRequest;
import ru.practicum.events.dto.UpdateEventAdminRequest;
import ru.practicum.events.dto.UpdateEventUserRequest;

import java.time.LocalDateTime;
import java.util.List;

public interface EventService {

    EventDto createEvent(Long userId, NewEventRequest newEventRequest);

    EventDto updateEventByOwner(Long userId, Long eventId, UpdateEventUserRequest updateEvent);

    EventDto getEventsByOwner(Long userId, Long eventId);

    List<EventShortDto> getEventsByOwner(Long userId, Integer from, Integer size);

    EventDto updateEventByAdmin(Long eventId, UpdateEventAdminRequest updateEvent);

    List<EventDtoWithViews> getEventsByAdminParams(List<Long> users, List<String> states, List<Long> categories,
                                                   LocalDateTime rangeStart, LocalDateTime rangeEnd,
                                                   Integer from, Integer size);

    List<EventShortDtoWithViews> getEvents(String text, List<Long> categories, Boolean paid, LocalDateTime rangeStart,
                                           LocalDateTime rangeEnd, Boolean onlyAvailable, String sort, Integer from,
                                           Integer size, HttpServletRequest request) throws Exception;

    EventDtoWithViews getEventById(Long eventId, HttpServletRequest request) throws Exception;

}