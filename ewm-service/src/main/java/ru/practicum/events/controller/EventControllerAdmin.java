package ru.practicum.events.controller;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;
import ru.practicum.events.EventService;
import ru.practicum.events.dto.EventDto;
import ru.practicum.events.dto.EventDtoWithViews;
import ru.practicum.events.dto.UpdateEventAdminRequest;

import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/admin/events")
public class EventControllerAdmin {

    private final EventService eventService;

    @PatchMapping("/{eventId}")
    public EventDto updateEventByAdmin(@PathVariable Long eventId,
                                       @RequestBody @Valid UpdateEventAdminRequest updateEventAdminRequest) {
        return eventService.updateEventByAdmin(eventId, updateEventAdminRequest);
    } //Обновить событие

    @GetMapping
    public List<EventDtoWithViews> getEventsByAdminParams(@RequestParam(required = false) List<Long> users,
                                                          @RequestParam(required = false) List<String> states,
                                                          @RequestParam(required = false) List<Long> categories,
                                                          @RequestParam(required = false) @DateTimeFormat(pattern =
                                                                  "yyyy-MM-dd HH:mm:ss") LocalDateTime rangeStart,
                                                          @RequestParam(required = false) @DateTimeFormat(pattern =
                                                                  "yyyy-MM-dd HH:mm:ss") LocalDateTime rangeEnd,
                                                          @RequestParam(value = "from", defaultValue = "0")
                                                          @PositiveOrZero Integer from,
                                                          @RequestParam(value = "size", defaultValue = "10")
                                                          @Positive Integer size) {


        return eventService.getEventsByAdminParams(users, states, categories, rangeStart, rangeEnd, from, size);
    }
}