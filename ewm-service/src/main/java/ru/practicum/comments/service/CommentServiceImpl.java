package ru.practicum.comments.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.practicum.comments.dto.CommentDto;
import ru.practicum.comments.dto.NewCommentRequest;
import ru.practicum.comments.mapper.CommentMapper;
import ru.practicum.comments.model.Comment;
import ru.practicum.comments.repository.CommentRepository;
import ru.practicum.events.dto.EventShortDto;
import ru.practicum.events.mapper.EventMapper;
import ru.practicum.events.model.Event;
import ru.practicum.events.repository.EventRepository;
import ru.practicum.exeptions.NotFoundException;
import ru.practicum.exeptions.ValidationException;
import ru.practicum.request.repository.RequestRepository;
import ru.practicum.user.dto.UserShortDto;
import ru.practicum.user.mapper.UserMapper;
import ru.practicum.user.model.User;
import ru.practicum.user.repository.UserRepository;
import ru.practicum.request.dto.ConfirmedRequests;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static ru.practicum.events.model.State.PUBLISHED;
import static ru.practicum.request.model.RequestStatus.CONFIRMED;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CommentServiceImpl implements CommentService {

    private final CommentRepository commentRepository;
    private final UserRepository userRepository;
    private final EventRepository eventRepository;
    private final RequestRepository requestRepository;

    @Override
    @Transactional
    public CommentDto addComment(Long userId, Long eventId, NewCommentRequest newCommentRequest) {

        User author = checkAndGetUser(userId);
        Event event = checkAndGetEvent(eventId);

        if (event.getState() != PUBLISHED) {
            throw new ValidationException("Эвента не существует");
        }

        Comment comment = commentRepository.save(CommentMapper.toEntity(newCommentRequest, author, event));
        UserShortDto userShort = UserMapper.toShortDto(author);
        EventShortDto eventShort = EventMapper.toShortDto(event,
                                                          requestRepository.countByEventIdAndStatus(eventId, CONFIRMED));

        return CommentMapper.toDto(comment, userShort, eventShort);
    }

    @Override
    @Transactional
    public CommentDto updateComment(Long userId, Long eventId, Long commentId, NewCommentRequest newCommentRequest) {


        Event event = checkAndGetEvent(eventId);

        Comment comment = commentRepository.findByIdAndAuthorId(commentId, userId).orElseThrow(() ->
                                                                                                       new NotFoundException(
                                                                                                               "Комментарий с id " +
                                                                                                                       commentId +
                                                                                                                       " не найден или " +
                                                                                                                       "принадлежит не " +
                                                                                                                       userId));
        if (comment.getEvent() != event) {
            throw new ValidationException("Комментарий не относится к событию");
        }

        comment.setText(newCommentRequest.getText());
        comment.setEdited(LocalDateTime.now());

        UserShortDto userShort = UserMapper.toShortDto(comment.getAuthor());
        EventShortDto eventShort = EventMapper.toShortDto(event,
                                                          requestRepository.countByEventIdAndStatus(eventId, CONFIRMED));

        return CommentMapper.toDto(comment, userShort, eventShort);

    }

    @Override
    public List<CommentDto> getCommentsByAuthor(Long userId, Integer from, Integer size) {

        User author = checkAndGetUser(userId);

        List<Comment> comments = commentRepository.findAllByAuthorId(userId, PageRequest.of(from / size, size));
        List<Long> eventIds = comments.stream().map(comment -> comment.getEvent().getId()).collect(Collectors.toList());

        Map<Long, Long> confirmedRequests = requestRepository.findAllByEventIdInAndStatus(eventIds, CONFIRMED)
                                                             .stream()
                                                             .collect(Collectors.toMap(ConfirmedRequests::getEvent,
                                                                                       ConfirmedRequests::getCount));

        UserShortDto userShort = UserMapper.toShortDto(author);
        List<CommentDto> result = new ArrayList<>();

        for (Comment c : comments) {
            Long eventId = c.getEvent().getId();
            EventShortDto eventShort = EventMapper.toShortDto(c.getEvent(), confirmedRequests.get(eventId));
            result.add(CommentMapper.toDto(c, userShort, eventShort));
        }

        return result;
    }

    @Override
    public List<CommentDto> getComments(Long eventId, Integer from, Integer size) {

        Event event = checkAndGetEvent(eventId);
        EventShortDto eventShort = EventMapper.toShortDto(event,
                                                          requestRepository.countByEventIdAndStatus(eventId, CONFIRMED));

        return commentRepository.findAllByEventId(eventId, PageRequest.of(from / size, size))
                                .stream()
                                .map(c -> CommentMapper.toDto(c, UserMapper.toShortDto(c.getAuthor()), eventShort))
                                .collect(Collectors.toList());
    }

    @Override
    public CommentDto getCommentById(Long commentId) {

        Comment comment = checkAndGetComment(commentId);
        UserShortDto userShort = UserMapper.toShortDto(comment.getAuthor());

        EventShortDto eventShort = EventMapper.toShortDto(comment.getEvent(),
                                                          requestRepository.countByEventIdAndStatus(comment.getEvent().getId(), CONFIRMED));

        return CommentMapper.toDto(comment, userShort, eventShort);
    }

    @Override
    @Transactional
    public void deleteComment(Long userId, Long commentId) {

        if (commentRepository.findByIdAndAuthorId(commentId, userId).isEmpty()) {
            throw new NotFoundException("Комментарий с id " + commentId + " не найдено или его инициатор не " + userId);
        }

        commentRepository.deleteById(commentId);

    }

    @Override
    @Transactional
    public void deleteComment(Long commentId) {

        checkAndGetComment(commentId);
        commentRepository.deleteById(commentId);

    }

    private User checkAndGetUser(Long userId) {
        return userRepository.findById(userId).orElseThrow(() ->
                                                                   new NotFoundException("Пользователь с id " + userId + " не найден"));
    }

    private Event checkAndGetEvent(Long eventId) {
        return eventRepository.findById(eventId).orElseThrow(() ->
                                                                     new NotFoundException("Событие с id " + eventId + " не найдено"));
    }

    private Comment checkAndGetComment(Long commentId) {
        return commentRepository.findById(commentId).orElseThrow(() ->
                                                                         new NotFoundException(
                                                                                 "Комментарий с id " + commentId + " не найден"));
    }
}
