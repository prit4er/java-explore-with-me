package ru.practicum.events.service;

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
import ru.practicum.category.repository.CategoryRepository;
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
import ru.practicum.events.repository.EventRepository;
import ru.practicum.exeptions.ConflictException;
import ru.practicum.exeptions.NotFoundException;
import ru.practicum.exeptions.ValidationException;
import ru.practicum.locations.repository.LocationRepository;
import ru.practicum.locations.mapper.LocationMapper;
import ru.practicum.locations.model.Location;
import ru.practicum.request.repository.RequestRepository;
import ru.practicum.request.dto.ConfirmedRequests;
import ru.practicum.user.repository.UserRepository;
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

    @Value("${app:ewm-service}") // Значение по умолчанию
    private String app;

    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    @Override
    @Transactional
    public EventDto createEvent(Long userId, NewEventRequest newEventRequest) {
        log.info("Создание нового события пользователем с ID {}", userId);
        validateEventDate(newEventRequest.getEventDate());

        User user = findUserById(userId);
        Category category = findCategoryById(newEventRequest.getCategory());
        Location location = getOrCreateLocation(LocationMapper.toEntity(newEventRequest.getLocation()));

        Event event = EventMapper.matToEvent(newEventRequest, user, category, location, PENDING);

        return EventMapper.mapToEventFullDto(eventRepository.save(event), 0L);
    }

    @Override
    @Transactional
    public EventDto updateEventByOwner(Long userId, Long eventId, UpdateEventUserRequest updateEvent) {
        log.info("Обновление события с ID {} пользователем с ID {}", eventId, userId);

        Event event = eventRepository.findByIdAndInitiatorId(eventId, userId)
                                     .orElseThrow(() -> new NotFoundException("Событие с ID " + eventId + " не найдено"));

        if (event.getState() == PUBLISHED) {
            throw new ConflictException("Нельзя обновить событие, которое уже опубликовано");
        }

        updateEventFields(event, updateEvent);

        return EventMapper.mapToEventFullDto(eventRepository.save(event),
                                             requestRepository.countByEventIdAndStatus(eventId, CONFIRMED));
    }

    @Override
    public EventDto getEventsByOwner(Long userId, Long eventId) {
        log.info("Получение события с ID {} пользователем с ID {}", eventId, userId);

        Event event = eventRepository.findByIdAndInitiatorId(eventId, userId)
                                     .orElseThrow(() -> new NotFoundException("Событие с ID " + eventId + " не найдено"));

        return EventMapper.mapToEventFullDto(event, requestRepository.countByEventIdAndStatus(eventId, CONFIRMED));
    }

    @Override
    public List<EventShortDto> getEventsByOwner(Long userId, Integer from, Integer size) {
        log.info("Получение событий пользователя с ID {}", userId);

        List<Event> events = eventRepository.findAllByInitiatorId(userId, PageRequest.of(from / size, size));
        Map<Long, Long> confirmedRequests = requestRepository.findAllByEventIdInAndStatus(
                                                                     events.stream().map(Event::getId).collect(Collectors.toList()),
                                                                     CONFIRMED)
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
        Event event = findEventById(eventId);

        if (updateEvent.getStateAction() != null) {
            updateEventState(event, updateEvent.getStateAction());
        }
        updateEventAttributes(event, updateEvent);

        return EventMapper.mapToEventFullDto(eventRepository.save(event),
                                             requestRepository.countByEventIdAndStatus(eventId, CONFIRMED));
    }

    @Override
    public List<EventDtoWithViews> getEventsByAdminParams(List<Long> users, List<String> states, List<Long> categories,
                                                          LocalDateTime rangeStart, LocalDateTime rangeEnd, Integer from, Integer size) {

        log.info("Запрос на получение полной информации по событиям");

        if (rangeStart != null && rangeEnd != null && rangeStart.isAfter(rangeEnd)) {
            throw new IllegalArgumentException("rangeStart should be before rangeEnd");
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

        // Создаем HitRequest
        HitRequest hitRequest = new HitRequest();
        hitRequest.setApp(app);
        hitRequest.setUri(request.getRequestURI());
        hitRequest.setIp(request.getRemoteAddr());
        hitRequest.setTimestamp(LocalDateTime.now().format(DATE_TIME_FORMATTER));

        statsClient.hit(hitRequest);

        Event event = eventRepository.findById(eventId).orElseThrow(() ->
                                                                            new NotFoundException(
                                                                                    "Событие с id " + eventId + " не найдено"));

        if (event.getState() != PUBLISHED) {
            throw new NotFoundException("Событие не опубликовано");
        }

        // Формируем список URI
        List<String> uris = List.of("/events/" + event.getId());

        // Форматируем временные рамки
        LocalDateTime startDate = event.getCreatedOn().minusSeconds(1);
        LocalDateTime endDate = LocalDateTime.now();

        List<ViewStats> response = statsClient.getStats(startDate, endDate, uris, true);

        // Формируем DTO результата
        EventDtoWithViews result;
        if (!response.isEmpty()) {
            result = EventMapper.mapToEventFullDtoWithViews(
                    event,
                    response.get(0).getHits(),
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
            throw new ValidationException("Время до начала события должно быть не менее 2 часов");
        }
    }

    private void updateEventFields(Event event, UpdateEventUserRequest updateEvent) {
        Optional.ofNullable(updateEvent.getAnnotation()).filter(annotation -> !annotation.isBlank())
                .ifPresent(event::setAnnotation);

        Optional.ofNullable(updateEvent.getCategory()).ifPresent(categoryId -> {
            Category category = findCategoryById(categoryId);
            event.setCategory(category);
        });

        Optional.ofNullable(updateEvent.getDescription()).filter(description -> !description.isBlank())
                .ifPresent(event::setDescription);

        Optional.ofNullable(updateEvent.getEventDate()).ifPresent(this::checkActualTime);

        Optional.ofNullable(updateEvent.getLocation())
                .map(LocationMapper::toEntity)
                .map(this::getOrCreateLocation)
                .ifPresent(event::setLocation);

        Optional.ofNullable(updateEvent.getPaid()).ifPresent(event::setPaid);
        Optional.ofNullable(updateEvent.getParticipantLimit()).ifPresent(event::setParticipantLimit);
        Optional.ofNullable(updateEvent.getRequestModeration()).ifPresent(event::setRequestModeration);

        Optional.ofNullable(updateEvent.getTitle()).filter(title -> !title.isBlank())
                .ifPresent(event::setTitle);

        Optional.ofNullable(updateEvent.getStateAction()).ifPresent(stateAction -> {
            StateActionPrivate action = StateActionPrivate.valueOf(stateAction);
            if (action == SEND_TO_REVIEW) {
                event.setState(PENDING);
            } else if (action == CANCEL_REVIEW) {
                event.setState(State.CANCELED);
            }
        });
    }

    private List<EventDtoWithViews> getFullEventsDetails(List<Event> events) {
        List<EventDtoWithViews> result = new ArrayList<>();

        if (events.isEmpty()) {
            return result;
        }

        // Формируем список URI
        List<String> uris = events.stream()
                                  .map(event -> "/events/" + event.getId())
                                  .collect(Collectors.toList());

        // Получаем самую раннюю дату создания события
        LocalDateTime startDate = events.stream()
                                        .map(Event::getCreatedOn)
                                        .min(LocalDateTime::compareTo)
                                        .orElse(LocalDateTime.now().minusYears(1));

        LocalDateTime endDate = LocalDateTime.now();

        try {
            // Получаем статистику просмотров
            List<ViewStats> stats = statsClient.getStats(startDate, endDate, uris, true);

            Map<String, Long> viewsMap = stats.stream()
                                              .collect(Collectors.toMap(
                                                      ViewStats::getUri,
                                                      ViewStats::getHits
                                              ));

            // Получаем подтвержденные запросы
            List<Long> eventIds = events.stream().map(Event::getId).collect(Collectors.toList());
            Map<Long, Long> confirmedRequests = requestRepository
                    .findAllByEventIdInAndStatus(eventIds, CONFIRMED)
                    .stream()
                    .collect(Collectors.toMap(
                            ConfirmedRequests::getEvent,
                            ConfirmedRequests::getCount
                    ));

            // Формируем результат
            for (Event event : events) {
                String eventUri = "/events/" + event.getId();
                Long views = viewsMap.getOrDefault(eventUri, 0L);
                Long confirmed = confirmedRequests.getOrDefault(event.getId(), 0L);

                result.add(EventMapper.mapToEventFullDtoWithViews(event, views, confirmed));
            }
        } catch (Exception e) {
            log.error("Error getting stats from stats-server", e);
            // Fallback - если не удалось получить статистику
            for (Event event : events) {
                result.add(EventMapper.mapToEventFullDtoWithViews(
                        event,
                        0L,
                        requestRepository.countByEventIdAndStatus(event.getId(), CONFIRMED)
                ));
            }
        }

        return result;
    }

    private List<EventShortDtoWithViews> getShortEventsDetails(List<Event> events) {
        List<EventShortDtoWithViews> result = new ArrayList<>();

        // Список URI событий
        List<String> uris = events.stream()
                                  .map(event -> String.format("/events/%s", event.getId()))
                                  .collect(Collectors.toList());

        // Минимальное время создания среди всех событий
        Optional<LocalDateTime> start = events.stream()
                                              .map(Event::getCreatedOn)
                                              .min(LocalDateTime::compareTo);

        if (start.isEmpty()) {
            return result; // Возврат пустого результата, если список событий пуст
        }

        // Используем LocalDateTime вместо строк
        LocalDateTime startTime = start.get();
        LocalDateTime endTime = LocalDateTime.now();

        // Получение статистики просмотров
        List<ViewStats> response = statsClient.getStats(startTime, endTime, uris, true);

        // Карта количества подтверждённых запросов по ID событий
        List<Long> ids = events.stream().map(Event::getId).collect(Collectors.toList());
        Map<Long, Long> confirmedRequests = requestRepository.findAllByEventIdInAndStatus(ids, CONFIRMED)
                                                             .stream()
                                                             .collect(Collectors.toMap(ConfirmedRequests::getEvent,
                                                                                       ConfirmedRequests::getCount));

        // Формирование результата
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

    private void validateEventDate(LocalDateTime eventTime) {
        if (eventTime.isBefore(LocalDateTime.now().plusHours(2))) {
            throw new ValidationException("Время до начала события должно быть не менее 2 часов");
        }
    }

    private User findUserById(Long userId) {
        return userRepository.findById(userId)
                             .orElseThrow(() -> new NotFoundException("Пользователь с ID " + userId + " не найден"));
    }

    private Category findCategoryById(Long categoryId) {
        return categoryRepository.findById(categoryId)
                                 .orElseThrow(() -> new NotFoundException("Категория с ID " + categoryId + " не найдена"));
    }

    private Event findEventById(Long eventId) {
        return eventRepository.findById(eventId)
                              .orElseThrow(() -> new NotFoundException("Событие с ID " + eventId + " не найдено"));
    }

    private void updateEventState(Event event, String stateAction) {
        StateActionAdmin action = StateActionAdmin.valueOf(stateAction);
        if (action == PUBLISH_EVENT) {
            if (!event.getState().equals(PENDING)) {
                throw new ConflictException("Событие должно быть в PENDING");
            }
            event.setState(PUBLISHED);
            event.setPublishedOn(LocalDateTime.now());
        } else if (action == REJECT_EVENT) {
            if (event.getState().equals(PUBLISHED)) {
                throw new ConflictException("Событие уже опубликовано");
            }
            event.setState(State.CANCELED);
        }
    }

    private void updateEventAttributes(Event event, UpdateEventAdminRequest updateEvent) {
        if (updateEvent.getAnnotation() != null && !updateEvent.getAnnotation().isBlank()) {
            event.setAnnotation(updateEvent.getAnnotation());
        }
        if (updateEvent.getCategory() != null) {
            Category category = findCategoryById(updateEvent.getCategory());
            event.setCategory(category);
        }
        if (updateEvent.getDescription() != null && !updateEvent.getDescription().isBlank()) {
            event.setDescription(updateEvent.getDescription());
        }
        if (updateEvent.getEventDate() != null) {
            validateEventDate(updateEvent.getEventDate());
            event.setEventDate(updateEvent.getEventDate());
        }
        if (updateEvent.getLocation() != null) {
            event.setLocation(getOrCreateLocation(LocationMapper.toEntity(updateEvent.getLocation())));
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
        if (updateEvent.getTitle() != null && !updateEvent.getTitle().isBlank()) {
            event.setTitle(updateEvent.getTitle());
        }
    }

    private Location getOrCreateLocation(Location location) {
        return Optional.ofNullable(locationRepository.findByLatAndLon(location.getLat(), location.getLon()))
                       .orElseGet(() -> locationRepository.save(location));
    }
}