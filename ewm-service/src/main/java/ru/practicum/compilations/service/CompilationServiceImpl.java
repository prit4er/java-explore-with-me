package ru.practicum.compilations.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.practicum.compilations.repository.CompilationRepository;
import ru.practicum.compilations.dto.CompilationDto;
import ru.practicum.compilations.dto.NewCompilationDto;
import ru.practicum.compilations.dto.UpdateCompilationRequest;
import ru.practicum.compilations.mapper.CompilationMapper;
import ru.practicum.compilations.model.Compilation;
import ru.practicum.events.repository.EventRepository;
import ru.practicum.events.mapper.EventMapper;
import ru.practicum.events.model.Event;
import ru.practicum.exeptions.NotFoundException;
import ru.practicum.request.repository.RequestRepository;
import ru.practicum.request.dto.ConfirmedRequests;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static ru.practicum.request.model.RequestStatus.CONFIRMED;

@Service
@Slf4j
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CompilationServiceImpl implements CompilationService {

    private final CompilationRepository compilationRepository;
    private final EventRepository eventRepository;
    private final RequestRepository requestRepository;

    @Override
    @Transactional
    public CompilationDto addCompilation(NewCompilationDto newCompilationDto) {
        log.info("Запрос на создание подборки");

        Compilation compilation = CompilationMapper.toEntity(newCompilationDto);

        if (newCompilationDto.getEvents() != null) {
            compilation.setEvents(eventRepository.findAllByIdIn(newCompilationDto.getEvents()));
        }

        CompilationDto compilationDto = CompilationMapper.toDto(compilationRepository.save(compilation));

        if (compilation.getEvents() != null) {
            List<Long> ids = compilation.getEvents().stream().map(Event::getId).collect(Collectors.toList());

            Map<Long, Long> confirmedRequests = requestRepository.findAllByEventIdInAndStatus(ids, CONFIRMED)
                                                                 .stream()
                                                                 .collect(Collectors.toMap(ConfirmedRequests::getEvent,
                                                                                           ConfirmedRequests::getCount));

            compilationDto.setEvents(compilation.getEvents().stream()
                                                .map(event -> EventMapper.toShortDto(event, confirmedRequests.get(event.getId())))
                                                .collect(Collectors.toList()));
        }

        return compilationDto;
    }

    @Override
    @Transactional
    public CompilationDto updateCompilation(Long compId, UpdateCompilationRequest updateCompilation) {
        log.info("Запрос на обновление подборки");

        Compilation compilation = getCompilation(compId);

        if (updateCompilation.getEvents() != null) {
            Set<Event> events = updateCompilation.getEvents().stream().map(id -> {
                Event event = new Event();
                event.setId(id);
                return event;
            }).collect(Collectors.toSet());
            compilation.setEvents(events);
        }

        if (updateCompilation.getPinned() != null) {
            compilation.setPinned(updateCompilation.getPinned());
        }

        String title = updateCompilation.getTitle();
        if (title != null && !title.isBlank()) {
            compilation.setTitle(title);
        }

        CompilationDto compilationDto = CompilationMapper.toDto(compilationRepository.save(compilation));

        if (compilation.getEvents() != null) {
            List<Long> ids = compilation.getEvents().stream().map(Event::getId).collect(Collectors.toList());
            Map<Long, Long> confirmedRequests = requestRepository.findAllByEventIdInAndStatus(ids, CONFIRMED)
                                                                 .stream()
                                                                 .collect(Collectors.toMap(ConfirmedRequests::getEvent,
                                                                                           ConfirmedRequests::getCount));
            compilationDto.setEvents(compilation.getEvents().stream()
                                                .map(event -> EventMapper.toShortDto(event, confirmedRequests.get(event.getId())))
                                                .collect(Collectors.toList()));
        }

        return compilationDto;
    }

    @Override
    public List<CompilationDto> getCompilations(Boolean pinned, Integer from, Integer size) {
        log.info("Запрос на получение подборок");

        Pageable pageable = PageRequest.of(from / size, size);
        List<Compilation> compilations;

        if (pinned != null) {
            compilations = compilationRepository.findAllByPinned(pinned, pageable);
        } else {
            compilations = compilationRepository.findAll(pageable).getContent();
        }

        List<Long> eventIds = compilations.stream()
                                          .flatMap(compilation -> compilation.getEvents().stream().map(Event::getId))
                                          .collect(Collectors.toList());

        Map<Long, Long> confirmedRequests = requestRepository.findAllByEventIdInAndStatus(eventIds, CONFIRMED)
                                                             .stream()
                                                             .collect(Collectors.toMap(ConfirmedRequests::getEvent,
                                                                                       ConfirmedRequests::getCount));

        return compilations.stream()
                           .map(compilation -> {
                               CompilationDto compilationDto = CompilationMapper.toDto(compilation);
                               if (compilation.getEvents() != null) {
                                   compilationDto.setEvents(compilation.getEvents().stream()
                                                                       .map(event -> EventMapper.toShortDto(event, confirmedRequests.get(
                                                                               event.getId())))
                                                                       .collect(Collectors.toList()));
                               }
                               return compilationDto;
                           })
                           .collect(Collectors.toList());
    }

    @Override
    public CompilationDto getCompilationById(Long compilationId) {
        Compilation compilation = getCompilation(compilationId);
        CompilationDto compilationDto = CompilationMapper.toDto(compilation);

        if (compilation.getEvents() != null) {
            List<Long> ids = compilation.getEvents().stream().map(Event::getId).collect(Collectors.toList());
            Map<Long, Long> confirmedRequests = requestRepository.findAllByEventIdInAndStatus(ids, CONFIRMED)
                                                                 .stream()
                                                                 .collect(Collectors.toMap(ConfirmedRequests::getEvent,
                                                                                           ConfirmedRequests::getCount));
            compilationDto.setEvents(compilation.getEvents().stream()
                                                .map(event -> EventMapper.toShortDto(event, confirmedRequests.get(event.getId())))
                                                .collect(Collectors.toList()));
        }

        return compilationDto;
    }

    @Override
    @Transactional
    public void deleteCompilation(Long compilationId) {
        getCompilation(compilationId);
        compilationRepository.deleteById(compilationId);
    }

    private Compilation getCompilation(Long compilationId) {
        return compilationRepository.findById(compilationId).orElseThrow(() ->
                                                                                 new NotFoundException(
                                                                                         "Подборка с id " + compilationId + " не найдена"));
    }
}
