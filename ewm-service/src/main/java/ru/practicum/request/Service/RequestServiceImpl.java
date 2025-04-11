package ru.practicum.request.Service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.practicum.events.EventRepository;
import ru.practicum.events.model.Event;
import ru.practicum.events.model.State;
import ru.practicum.exeptions.ConflictException;
import ru.practicum.exeptions.ForbiddenException;
import ru.practicum.exeptions.NotFoundException;
import ru.practicum.request.RequestRepository;
import ru.practicum.request.dto.EventRequestStatusUpdateRequest;
import ru.practicum.request.dto.EventRequestStatusUpdateResult;
import ru.practicum.request.dto.RequestDto;
import ru.practicum.request.mapper.ParticipationRequest;
import ru.practicum.request.mapper.RequestMapper;
import ru.practicum.request.model.RequestStatus;
import ru.practicum.user.UserRepository;
import ru.practicum.user.model.User;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import static ru.practicum.request.model.RequestStatus.CONFIRMED;
import static ru.practicum.request.model.RequestStatus.PENDING;
import static ru.practicum.request.model.RequestStatus.REJECTED;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class RequestServiceImpl implements RequestService {

    private final RequestRepository requestRepository;
    private final EventRepository eventRepository;
    private final UserRepository userRepository;

    @Override
    @Transactional
    public RequestDto addRequest(Long userId, Long eventId) {
        log.info("Добавление запроса от пользователя с ID {} для события с ID {}", userId, eventId);

        Event event = getEvent(eventId);
        User user = getUser(userId);

        validateAddRequest(userId, event);

        ParticipationRequest request = new ParticipationRequest();
        request.setCreated(LocalDateTime.now());
        request.setEvent(event);
        request.setRequester(user);
        request.setStatus(determineRequestStatus(event));

        return RequestMapper.toDto(requestRepository.save(request));
    }

    @Override
    @Transactional
    public EventRequestStatusUpdateResult updateRequestsStatus(Long userId, Long eventId,
                                                               EventRequestStatusUpdateRequest statusUpdateRequest) {
        log.info("Обновление статусов запросов для события с ID {} инициатором с ID {}", eventId, userId);

        Event event = getEventByInitiator(userId, eventId);
        validateParticipantLimit(event);

        List<ParticipationRequest> requests = requestRepository.findAllByEventIdAndIdInAndStatus(
                eventId, statusUpdateRequest.getRequestIds(), PENDING);

        return processRequestStatusUpdate(requests, statusUpdateRequest.getStatus(), event);
    }

    @Override
    @Transactional
    public RequestDto cancelRequest(Long userId, Long requestId) {
        log.info("Отмена запроса с ID {} пользователем с ID {}", requestId, userId);

        ParticipationRequest request = getRequestByUser(userId, requestId);
        validateRequestCancellation(request);

        request.setStatus(RequestStatus.CANCELED);
        return RequestMapper.toDto(requestRepository.save(request));
    }

    @Override
    public List<RequestDto> getRequestsByEventOwner(Long userId, Long eventId) {
        log.info("Получение запросов для события с ID {} инициатором с ID {}", eventId, userId);

        getEventByInitiator(userId, eventId);

        return requestRepository.findAllByEventId(eventId).stream()
                                .map(RequestMapper::toDto)
                                .collect(Collectors.toList());
    }

    @Override
    public List<RequestDto> getRequestsByUser(Long userId) {
        log.info("Получение запросов пользователя с ID {}", userId);

        checkUserExists(userId);

        return requestRepository.findAllByRequesterId(userId).stream()
                                .map(RequestMapper::toDto)
                                .collect(Collectors.toList());
    }

    // Вспомогательные методы

    private Event getEvent(Long eventId) {
        return eventRepository.findById(eventId).orElseThrow(() ->
                                                                     new NotFoundException("Событие с ID " + eventId + " не найдено"));
    }

    private Event getEventByInitiator(Long userId, Long eventId) {
        return eventRepository.findByIdAndInitiatorId(eventId, userId).orElseThrow(() ->
                                                                                           new NotFoundException("Событие с ID " + eventId +
                                                                                                                         " не найдено для" +
                                                                                                                         " инициатора с " +
                                                                                                                         "ID " +
                                                                                                                         userId));
    }

    private User getUser(Long userId) {
        return userRepository.findById(userId).orElseThrow(() ->
                                                                   new NotFoundException("Пользователь с ID " + userId + " не найден"));
    }

    private void checkUserExists(Long userId) {
        if (!userRepository.existsById(userId)) {
            throw new NotFoundException("Пользователь с ID " + userId + " не найден");
        }
    }

    private ParticipationRequest getRequestByUser(Long userId, Long requestId) {
        return requestRepository.findByIdAndRequesterId(requestId, userId).orElseThrow(() ->
                                                                                               new NotFoundException(
                                                                                                       "Запрос с ID " + requestId +
                                                                                                               " не найден или " +
                                                                                                               "пользователь с ID " +
                                                                                                               userId +
                                                                                                               " не является инициатором"));
    }

    private void validateAddRequest(Long userId, Event event) {
        if (!event.getState().equals(State.PUBLISHED)) {
            throw new ForbiddenException("Событие должно быть опубликовано");
        }
        if (requestRepository.existsByRequesterIdAndEventId(userId, event.getId())) {
            throw new ForbiddenException("Запрос уже существует");
        }
        if (userId.equals(event.getInitiator().getId())) {
            throw new ForbiddenException("Создатель события не может отправить запрос на участие");
        }
        validateParticipantLimit(event);
    }

    private void validateParticipantLimit(Event event) {
        if (event.getParticipantLimit() > 0 &&
                requestRepository.countByEventIdAndStatus(event.getId(), CONFIRMED) >= event.getParticipantLimit()) {
            throw new ConflictException("Достигнуто максимальное количество участников");
        }
    }

    private void validateRequestCancellation(ParticipationRequest request) {
        if (!request.getStatus().equals(PENDING)) {
            throw new ForbiddenException("Запрос должен быть в статусе PENDING для отмены");
        }
    }

    private RequestStatus determineRequestStatus(Event event) {
        return event.getRequestModeration() && event.getParticipantLimit() > 0 ? PENDING : CONFIRMED;
    }

    private EventRequestStatusUpdateResult processRequestStatusUpdate(
            List<ParticipationRequest> requests, RequestStatus targetStatus, Event event) {
        List<RequestDto> confirmed = new ArrayList<>();
        List<RequestDto> rejected = new ArrayList<>();
        long confirmedRequests = requestRepository.countByEventIdAndStatus(event.getId(), CONFIRMED);

        for (ParticipationRequest request : requests) {
            if (targetStatus == REJECTED) {
                request.setStatus(REJECTED);
                rejected.add(RequestMapper.toDto(request));
            } else if (targetStatus == CONFIRMED &&
                    (event.getParticipantLimit() == 0 || confirmedRequests < event.getParticipantLimit())) {
                request.setStatus(CONFIRMED);
                confirmed.add(RequestMapper.toDto(request));
                confirmedRequests++;
            } else {
                request.setStatus(REJECTED);
                rejected.add(RequestMapper.toDto(request));
            }
        }

        requestRepository.saveAll(requests);
        return new EventRequestStatusUpdateResult(confirmed, rejected);
    }
}