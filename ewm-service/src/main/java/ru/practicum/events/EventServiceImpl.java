package ru.practicum.events;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.practicum.HitRequest;
import ru.practicum.StatsClient;
import ru.practicum.ViewStats;
import ru.practicum.category.CategoryRepository;
import ru.practicum.category.model.Category;
import ru.practicum.events.dto.EventDto;
import ru.practicum.events.dto.EventDtoWithViews;
import ru.practicum.events.dto.EventShortDto;
import ru.practicum.events.dto.EventShortDtoWithViews;
import ru.practicum.events.dto.NewEventRequest;
import ru.practicum.events.dto.UpdateEventAdminRequest;
import ru.practicum.events.dto.UpdateEventUserRequest;
import ru.practicum.events.mapper.EventMapper;
import ru.practicum.events.model.Event;
import ru.practicum.events.model.State;
import ru.practicum.events.model.StateActionAdmin;
import ru.practicum.events.model.StateActionPrivate;
import ru.practicum.exeptions.ForbiddenException;
import ru.practicum.exeptions.NotFoundException;
import ru.practicum.exeptions.ValidationException;
import ru.practicum.locations.LocationRepository;
import ru.practicum.locations.mapper.LocationMapper;
import ru.practicum.locations.model.Location;
import ru.practicum.request.RequestRepository;
import ru.practicum.request.dto.ConfirmedRequests;
import ru.practicum.user.UserRepository;
import ru.practicum.user.model.User;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

import static ru.practicum.events.model.State.PENDING;
import static ru.practicum.events.model.State.PUBLISHED;
import static ru.practicum.events.model.StateActionAdmin.PUBLISH_EVENT;
import static ru.practicum.events.model.StateActionAdmin.REJECT_EVENT;
import static ru.practicum.events.model.StateActionPrivate.CANCEL_REVIEW;
import static ru.practicum.events.model.StateActionPrivate.SEND_TO_REVIEW;
import static ru.practicum.request.model.RequestStatus.CONFIRMED;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class EventServiceImpl implements EventService {

    private final UserRepository userRepository;
    private final CategoryRepository categoryRepository;
    private final EventRepository eventRepository;
    private final LocationRepository locationRepository;
    private final RequestRepository requestRepository;
    private final StatsClient statsClient;

    @Value("${app:ewm-service}") // с fallback значением
    private String app;

    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    @Override
    @Transactional
    public EventDto createEvent(Long userId, NewEventRequest newEventRequest) {
        log.info("Запрос на новое событие");

        checkActualTime(newEventRequest.getEventDate());

        User user = userRepository.findById(userId).orElseThrow(() ->
                                                                        new NotFoundException("Пользователь с " + userId + " не найден"));

        Long categoryId = newEventRequest.getCategory();

        Category category = categoryRepository.findById(categoryId).orElseThrow(() ->
                                                                                        new NotFoundException(
                                                                                                "Категория с id " + categoryId +
                                                                                                        " не найдена"));

        Location location = getOrCreateLocation(LocationMapper.mapToLocation(newEventRequest.getLocation()));

        Event event = EventMapper.matToEvent(newEventRequest, user, category, location, PENDING);

        return EventMapper.mapToEventFullDto(eventRepository.save(event), 0L);
    }

    @Override
    @Transactional
    public EventDto updateEventByOwner(Long userId, Long eventId, UpdateEventUserRequest updateEvent) {
        log.info("Запрос на обновления события");

        Event event = eventRepository.findByIdAndInitiatorId(eventId, userId).orElseThrow(() ->
                                                                                                  new NotFoundException(
                                                                                                          "Событие с id " + eventId +
                                                                                                                  " не найдено"));

        if (event.getState() == PUBLISHED) {
            throw new ForbiddenException("Нельзя обновить событие которое уже опубликовано");
        }

        String annotation = updateEvent.getAnnotation();
        if (annotation != null && !annotation.isBlank()) {
            event.setAnnotation(annotation);
        }

        if (updateEvent.getCategory() != null) {
            Category category = categoryRepository.findById(updateEvent.getCategory()).orElseThrow(() ->
                                                                                                           new NotFoundException(
                                                                                                                   "Категория с id " +
                                                                                                                           updateEvent.getCategory() +
                                                                                                                           " не найдена"));

            event.setCategory(category);
        }

        String description = updateEvent.getDescription();
        if (description != null && !description.isBlank()) {
            event.setDescription(description);
        }

        LocalDateTime eventDate = updateEvent.getEventDate();
        if (eventDate != null) {
            checkActualTime(eventDate);
            event.setEventDate(eventDate);
        }

        if (updateEvent.getLocation() != null) {
            Location location = getOrCreateLocation(LocationMapper.mapToLocation(updateEvent.getLocation()));
            event.setLocation(location);
        }

        if (updateEvent.getPaid() != null) {
            event.setPaid(updateEvent.getPaid());
        }

        if (updateEvent.getParticipantLimit() != null) {
            event.setParticipantLimit(updateEvent.getParticipantLimit());
        }

        if (updateEvent.getRequestModeration() != null) {
            event.setRequestModeration(updateEvent.getRequestModeration());
        }

        String title = updateEvent.getTitle();
        if (title != null && !title.isBlank()) {
            event.setTitle(title);
        }

        if (updateEvent.getStateAction() != null) {
            StateActionPrivate stateActionPrivate = StateActionPrivate.valueOf(updateEvent.getStateAction());
            if (stateActionPrivate.equals(SEND_TO_REVIEW)) {
                event.setState(PENDING);
            } else if (stateActionPrivate.equals(CANCEL_REVIEW)) {
                event.setState(State.CANCELED);
            }
        }

        return EventMapper.mapToEventFullDto(eventRepository.save(event),
                                             requestRepository.countByEventIdAndStatus(eventId, CONFIRMED));
    }

    @Override
    public EventDto getEventsByOwner(Long userId, Long eventId) {
        log.info("Запрос на получение события");

        Event event = eventRepository.findByIdAndInitiatorId(eventId, userId).orElseThrow(() ->
                                                                                                  new NotFoundException(
                                                                                                          "Событие с id " + eventId +
                                                                                                                  " не найдено"));

        return EventMapper.mapToEventFullDto(event,
                                             requestRepository.countByEventIdAndStatus(eventId, CONFIRMED));
    }

    @Override
    public List<EventShortDto> getEventsByOwner(Long userId, Integer from, Integer size) {
        log.info("Запрос на получение событий");

        List<Event> events = eventRepository.findAllByInitiatorId(userId, PageRequest.of(from / size, size));
        List<Long> ids = events.stream().map(Event::getId).collect(Collectors.toList());
        Map<Long, Long> confirmedRequests = requestRepository.findAllByEventIdInAndStatus(ids, CONFIRMED)
                                                             .stream()
                                                             .collect(Collectors.toMap(ConfirmedRequests::getEvent,
                                                                                       ConfirmedRequests::getCount));
        return events.stream()
                     .map(event -> EventMapper.mapToEventShortDto(event, confirmedRequests.getOrDefault(event.getId(), 0L)))
                     .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public EventDto updateEventByAdmin(Long eventId, UpdateEventAdminRequest updateEvent) {
        log.info("Запрос на обновление события");

        Event event = eventRepository.findById(eventId).orElseThrow(() ->
                                                                            new NotFoundException(
                                                                                    "Событие с id " + eventId + " не найдено"));

        if (updateEvent.getStateAction() != null) {
            StateActionAdmin stateAction = StateActionAdmin.valueOf(updateEvent.getStateAction());
            if (!event.getState().equals(PENDING) && stateAction.equals(PUBLISH_EVENT)) {
                throw new ForbiddenException("Событие должно быть в PENDING");
            }
            if (event.getState().equals(PUBLISHED) && stateAction.equals(REJECT_EVENT)) {
                throw new ForbiddenException("Событие уже опубликовано");
            }
            if (stateAction.equals(PUBLISH_EVENT)) {
                event.setState(PUBLISHED);
                event.setPublishedOn(LocalDateTime.now());
            } else if (stateAction.equals(REJECT_EVENT)) {
                event.setState(State.CANCELED);
            }
        }
        String annotation = updateEvent.getAnnotation();
        if (annotation != null && !annotation.isBlank()) {
            event.setAnnotation(annotation);
        }
        if (updateEvent.getCategory() != null) {
            Category category = categoryRepository.findById(updateEvent.getCategory()).orElseThrow(() ->
                                                                                                           new NotFoundException(
                                                                                                                   "Категория с id " +
                                                                                                                           updateEvent.getCategory() +
                                                                                                                           " не найдена"));

            event.setCategory(category);
        }
        String description = updateEvent.getDescription();
        if (description != null && !description.isBlank()) {
            event.setDescription(description);
        }
        LocalDateTime eventDate = updateEvent.getEventDate();
        if (eventDate != null) {
            checkActualTime(eventDate);
            event.setEventDate(eventDate);
        }
        if (updateEvent.getLocation() != null) {
            event.setLocation(getOrCreateLocation(LocationMapper.mapToLocation(updateEvent.getLocation())));
        }
        if (updateEvent.getPaid() != null) {
            event.setPaid(updateEvent.getPaid());
        }
        if (updateEvent.getParticipantLimit() != null) {
            event.setParticipantLimit(updateEvent.getParticipantLimit());
        }
        if (updateEvent.getRequestModeration() != null) {
            event.setRequestModeration(updateEvent.getRequestModeration());
        }
        String title = updateEvent.getTitle();
        if (title != null && !title.isBlank()) {
            event.setTitle(title);
        }
        return EventMapper.mapToEventFullDto(eventRepository.save(event),
                                             requestRepository.countByEventIdAndStatus(eventId, CONFIRMED));
    }

    public List<EventDtoWithViews> getEventsByAdminParams(List<Long> users, List<String> states, List<Long> categories,
                                                          LocalDateTime rangeStart, LocalDateTime rangeEnd,
                                                          Integer from, Integer size) {
        log.info("Запрос на получение полной информации по событиям");

        if (rangeStart != null && rangeEnd != null && rangeStart.isAfter(rangeEnd)) {
            throw new ValidationException("Некорректный запрос");
        }
        Specification<Event> specification = Specification.where(null);
        if (users != null) {
            specification = specification.and((root, query, criteriaBuilder) ->
                                                      root.get("initiator").get("id").in(users));
        }
        if (states != null) {
            specification = specification.and((root, query, criteriaBuilder) ->
                                                      root.get("state").as(String.class).in(states));
        }
        if (categories != null) {
            specification = specification.and((root, query, criteriaBuilder) ->
                                                      root.get("category").get("id").in(categories));
        }

        if (rangeStart != null) {
            specification = specification.and((root, query, criteriaBuilder) ->
                                                      criteriaBuilder.greaterThanOrEqualTo(root.get("eventDate"), rangeStart));
        }
        if (rangeEnd != null) {
            specification = specification.and((root, query, criteriaBuilder) ->
                                                      criteriaBuilder.lessThanOrEqualTo(root.get("eventDate"), rangeEnd));
        }

        List<Event> events = eventRepository.findAll(specification, PageRequest.of(from / size, size)).getContent();

        if (events.isEmpty()) {
            return new ArrayList<>();
        }

        return getFullEventsDetails(events);
    }

    @Override
    public List<EventShortDtoWithViews> getEvents(String text, List<Long> categories, Boolean paid, LocalDateTime rangeStart,
                                                  LocalDateTime rangeEnd, Boolean onlyAvailable, String sort, Integer from,
                                                  Integer size, HttpServletRequest request) {
        log.info("Запрос на получение краткой информации по событиям");

        if (rangeStart != null && rangeEnd != null && rangeStart.isAfter(rangeEnd)) {
            throw new ValidationException("Не верный диапазон поиска");
        }

        Specification<Event> specification = Specification.where(null);
        if (text != null) {
            specification = specification.and((root, query, criteriaBuilder) ->
                                                      criteriaBuilder.or(
                                                              criteriaBuilder.like(criteriaBuilder.lower(root.get("annotation")),
                                                                                   "%" + text.toLowerCase() + "%"),
                                                              criteriaBuilder.like(criteriaBuilder.lower(root.get("description")),
                                                                                   "%" + text.toLowerCase() + "%")
                                                      ));
        }
        if (categories != null) {
            specification = specification.and((root, query, criteriaBuilder) ->
                                                      root.get("category").get("id").in(categories));
        }
        if (paid != null) {
            specification = specification.and((root, query, criteriaBuilder) ->
                                                      criteriaBuilder.equal(root.get("paid"), paid));
        }

        LocalDateTime now = LocalDateTime.now();
        LocalDateTime startDateTime = Objects.requireNonNullElseGet(rangeStart, () -> now);
        specification = specification.and((root, query, criteriaBuilder) ->
                                                  criteriaBuilder.greaterThan(root.get("eventDate"), startDateTime));
        if (rangeEnd != null) {
            specification = specification.and((root, query, criteriaBuilder) ->
                                                      criteriaBuilder.lessThan(root.get("eventDate"), rangeEnd));
        }
        if (onlyAvailable != null && onlyAvailable) {
            specification = specification.and((root, query, criteriaBuilder) ->
                                                      criteriaBuilder.greaterThanOrEqualTo(root.get("participantLimit"), 0));
        }
        specification = specification.and((root, query, criteriaBuilder) ->
                                                  criteriaBuilder.equal(root.get("state"), PUBLISHED));

        PageRequest pageRequest = switch (sort) {
            case "EVENT_DATE" -> PageRequest.of(from / size, size, Sort.by("eventDate"));
            case "VIEWS" -> PageRequest.of(from / size, size, Sort.by("views").descending());
            default -> throw new ValidationException("Неправильный sort: " + sort);
        };

        List<Event> events = eventRepository.findAll(specification, pageRequest).getContent();

        if (events.isEmpty()) {
            return new ArrayList<>();
        }

        // Создаем HitRequest вместо NewEventRequest
        HitRequest hitRequest = new HitRequest();
        hitRequest.setApp(app);
        hitRequest.setUri(request.getRequestURI());
        hitRequest.setIp(request.getRemoteAddr());
        hitRequest.setTimestamp(LocalDateTime.now().format(DATE_TIME_FORMATTER));

        statsClient.hit(hitRequest);

        return getShortEventsDetails(events);
    }

    @Override
    public EventDtoWithViews getEventById(Long eventId, HttpServletRequest request) {
        log.info("Запрос на получение полной информации по событию");

        // Создаем HitRequest вместо NewEventRequest
        HitRequest hitRequest = new HitRequest();
        hitRequest.setApp(app);
        hitRequest.setUri(request.getRequestURI());
        hitRequest.setIp(request.getRemoteAddr());
        hitRequest.setTimestamp(LocalDateTime.now().format(DATE_TIME_FORMATTER));

        statsClient.hit(hitRequest);

        Event event = eventRepository.findById(eventId).orElseThrow(() ->
                                                                            new NotFoundException("Событие с id " + eventId + " не найдено"));

        if (event.getState() != PUBLISHED) {
            throw new NotFoundException("Событие не опубликовано");
        }

        String start = event.getCreatedOn().minusSeconds(1).format(DATE_TIME_FORMATTER);
        String end = LocalDateTime.now().format(DATE_TIME_FORMATTER);

        List<ViewStats> response = statsClient.getStats(
                start,
                end,
                List.of(request.getRequestURI()),
                true
        );

        EventDtoWithViews result;
        if (!response.isEmpty()) {
            result = EventMapper.mapToEventFullDtoWithViews(
                    event,
                    response.getFirst().getHits(),
                    requestRepository.countByEventIdAndStatus(eventId, CONFIRMED)
            );
        } else {
            result = EventMapper.mapToEventFullDtoWithViews(
                    event,
                    0L,
                    requestRepository.countByEventIdAndStatus(eventId, CONFIRMED)
            );
        }

        return result;
    }

    private void checkActualTime(LocalDateTime eventTime) {
        if (eventTime.isBefore(LocalDateTime.now().plusHours(2))) {
            throw new ValidationException("Время до начала события не может быть меньше 2х часов");
        }
    }

    private Location getOrCreateLocation(Location location) {
        if (locationRepository.existsByLatAndLon(location.getLat(), location.getLon())) {
            return locationRepository.findByLatAndLon(location.getLat(), location.getLon());
        } else {
            return locationRepository.save(location);
        }
    }

    private List<EventShortDtoWithViews> getShortEventsDetails(List<Event> events) {
        List<EventShortDtoWithViews> result = new ArrayList<>();

        List<String> uris = events.stream()
                                  .map(event -> String.format("/events/%s", event.getId()))
                                  .collect(Collectors.toList());

        Optional<LocalDateTime> start = events.stream()
                                              .map(Event::getCreatedOn)
                                              .min(LocalDateTime::compareTo);

        if (start.isEmpty()) {
            return result;
        }

        String startStr = start.get().format(DATE_TIME_FORMATTER);
        String endStr = LocalDateTime.now().format(DATE_TIME_FORMATTER);

        List<ViewStats> response = statsClient.getStats(startStr, endStr, uris, true);

        List<Long> ids = events.stream().map(Event::getId).collect(Collectors.toList());
        Map<Long, Long> confirmedRequests = requestRepository.findAllByEventIdInAndStatus(ids, CONFIRMED)
                                                             .stream()
                                                             .collect(Collectors.toMap(ConfirmedRequests::getEvent, ConfirmedRequests::getCount));

        for (Event event : events) {
            Long views = response.stream()
                                 .filter(stats -> stats.getUri().equals("/events/" + event.getId()))
                                 .findFirst()
                                 .map(ViewStats::getHits)
                                 .orElse(0L);

            result.add(EventMapper.mapToEventShortDtoWithViews(
                    event,
                    views,
                    confirmedRequests.getOrDefault(event.getId(), 0L)
            ));
        }

        return result;
    }

    private List<EventDtoWithViews> getFullEventsDetails(List<Event> events) {
        List<EventDtoWithViews> result = new ArrayList<>();

        List<String> uris = events.stream()
                                  .map(event -> String.format("/events/%s", event.getId()))
                                  .collect(Collectors.toList());

        Optional<LocalDateTime> start = events.stream()
                                              .map(Event::getCreatedOn)
                                              .min(LocalDateTime::compareTo);

        if (start.isEmpty()) {
            return result;
        }

        String startStr = start.get().format(DATE_TIME_FORMATTER);
        String endStr = LocalDateTime.now().format(DATE_TIME_FORMATTER);

        List<ViewStats> response = statsClient.getStats(startStr, endStr, uris, true);

        List<Long> ids = events.stream().map(Event::getId).collect(Collectors.toList());
        Map<Long, Long> confirmedRequests = requestRepository.findAllByEventIdInAndStatus(ids, CONFIRMED)
                                                             .stream()
                                                             .collect(Collectors.toMap(ConfirmedRequests::getEvent, ConfirmedRequests::getCount));

        for (Event event : events) {
            Long views = response.stream()
                                 .filter(stats -> stats.getUri().equals("/events/" + event.getId()))
                                 .findFirst()
                                 .map(ViewStats::getHits)
                                 .orElse(0L);

            result.add(EventMapper.mapToEventFullDtoWithViews(
                    event,
                    views,
                    confirmedRequests.getOrDefault(event.getId(), 0L)
            ));
        }

        return result;
    }
}