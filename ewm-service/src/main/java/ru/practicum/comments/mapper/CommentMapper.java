package ru.practicum.comments.mapper;

import lombok.experimental.UtilityClass;
import ru.practicum.comments.dto.CommentDto;
import ru.practicum.comments.dto.NewCommentRequest;
import ru.practicum.comments.model.Comment;
import ru.practicum.events.dto.EventShortDto;
import ru.practicum.events.model.Event;
import ru.practicum.user.dto.UserShortDto;
import ru.practicum.user.model.User;

import java.time.LocalDateTime;

@UtilityClass
public class CommentMapper {

    public Comment toEntity(NewCommentRequest newCommentRequest, User author, Event event) {
        Comment comment = new Comment();
        comment.setAuthor(author);
        comment.setEvent(event);
        comment.setText(newCommentRequest.getText());
        comment.setCreated(LocalDateTime.now());
        return comment;
    }

    public CommentDto toDto(Comment comment, UserShortDto author, EventShortDto event) {
        return new CommentDto(
                comment.getId(),
                comment.getText(),
                author,
                event,
                comment.getCreated(),
                comment.getEdited()
        );
    }
}
