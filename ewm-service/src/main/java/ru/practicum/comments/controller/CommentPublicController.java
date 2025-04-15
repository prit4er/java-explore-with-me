package ru.practicum.comments.controller;

import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import ru.practicum.comments.dto.CommentDto;
import ru.practicum.comments.service.CommentService;

import java.util.List;

@Validated
@RestController
@RequestMapping("/comments")
@RequiredArgsConstructor
public class CommentPublicController {

    private final CommentService commentService;

    @GetMapping("/event/{eventId}")
    List<CommentDto> getComments(@PathVariable Long eventId,
                                 @RequestParam(value = "from", defaultValue = "0") @PositiveOrZero Integer from,
                                 @RequestParam(value = "size", defaultValue = "10") @Positive Integer size) {
        return commentService.getComments(eventId, from, size);
    }

    @GetMapping("/{commentId}")
    CommentDto getCommentById(@PathVariable Long commentId) {
        return commentService.getCommentById(commentId);
    }
}
