package ru.practicum.events.mapper;

import lombok.experimental.UtilityClass;
import ru.practicum.category.model.Category;
import ru.practicum.category.mapper.CategoryMapper;
import ru.practicum.events.dto.EventDto;
import ru.practicum.events.dto.EventDtoWithViews;
import ru.practicum.events.dto.EventShortDto;
import ru.practicum.events.dto.EventShortDtoWithViews;
import ru.practicum.events.dto.NewEventRequest;
import ru.practicum.events.model.Event;
import ru.practicum.events.model.State;
import ru.practicum.locations.mapper.LocationMapper;
import ru.practicum.locations.model.Location;
import ru.practicum.user.model.User;
import ru.practicum.user.mapper.UserMapper;

import java.time.LocalDateTime;

@UtilityClass
public class EventMapper {

    public Event toEntity(NewEventRequest newEventRequest) {
        return Event.builder()
                    .annotation(newEventRequest.getAnnotation())
                    .description(newEventRequest.getDescription())
                    .eventDate(newEventRequest.getEventDate())
                    .location(LocationMapper.toEntity(newEventRequest.getLocation()))
                    .paid(newEventRequest.getPaid())
                    .participantLimit(newEventRequest.getParticipantLimit())
                    .requestModeration(newEventRequest.getRequestModeration())
                    .title(newEventRequest.getTitle())
                    .build();
    }

    public Event toEntity(NewEventRequest newEventRequest, User user, Category category, Location location, State state) {
        return Event.builder()
                    .annotation(newEventRequest.getAnnotation())
                    .category(category)
                    .createdOn(LocalDateTime.now())
                    .description(newEventRequest.getDescription())
                    .eventDate(newEventRequest.getEventDate())
                    .initiator(user)
                    .location(location)
                    .paid(newEventRequest.getPaid())
                    .participantLimit(newEventRequest.getParticipantLimit())
                    .requestModeration(newEventRequest.getRequestModeration())
                    .state(state)
                    .title(newEventRequest.getTitle())
                    .build();
    }

    public EventDto toDto(Event event, Long confirmedRequests) {
        return EventDto.builder()
                       .id(event.getId())
                       .annotation(event.getAnnotation())
                       .category(CategoryMapper.toDto(event.getCategory()))
                       .confirmedRequests(confirmedRequests)
                       .createdOn(event.getCreatedOn())
                       .description(event.getDescription())
                       .eventDate(event.getEventDate())
                       .initiator(UserMapper.toShortDto(event.getInitiator()))
                       .location(LocationMapper.toDto(event.getLocation()))
                       .paid(event.getPaid())
                       .participantLimit(event.getParticipantLimit())
                       .publishedOn(event.getPublishedOn())
                       .requestModeration(event.getRequestModeration())
                       .state(event.getState())
                       .title(event.getTitle())
                       .build();
    }

    public EventShortDto toShortDto(Event event, Long confirmedRequests) {
        return EventShortDto.builder()
                            .id(event.getId())
                            .annotation(event.getAnnotation())
                            .category(CategoryMapper.toDto(event.getCategory()))
                            .confirmedRequests(confirmedRequests)
                            .eventDate(event.getEventDate())
                            .initiator(UserMapper.toShortDto(event.getInitiator()))
                            .paid(event.getPaid())
                            .title(event.getTitle())
                            .build();
    }

    public EventDtoWithViews toDtoWithViews(Event event, Long views, Long confirmedRequests, Long comments) {
        return EventDtoWithViews.builder()
                                .id(event.getId())
                                .annotation(event.getAnnotation())
                                .category(CategoryMapper.toDto(event.getCategory()))
                                .confirmedRequests(confirmedRequests)
                                .createdOn(event.getCreatedOn())
                                .description(event.getDescription())
                                .eventDate(event.getEventDate())
                                .initiator(UserMapper.toShortDto(event.getInitiator()))
                                .location(LocationMapper.toDto(event.getLocation()))
                                .paid(event.getPaid())
                                .participantLimit(event.getParticipantLimit())
                                .publishedOn(event.getPublishedOn())
                                .requestModeration(event.getRequestModeration())
                                .state(event.getState())
                                .title(event.getTitle())
                                .views(views)
                                .comments(comments)
                                .build();
    }

    public EventShortDtoWithViews toShortDtoWithViews(Event event, Long views, Long confirmedRequests) {
        return EventShortDtoWithViews.builder()
                                     .id(event.getId())
                                     .annotation(event.getAnnotation())
                                     .category(CategoryMapper.toDto(event.getCategory()))
                                     .confirmedRequests(confirmedRequests)
                                     .eventDate(event.getEventDate())
                                     .initiator(UserMapper.toShortDto(event.getInitiator()))
                                     .paid(event.getPaid())
                                     .title(event.getTitle())
                                     .views(views)
                                     .build();
    }

}
