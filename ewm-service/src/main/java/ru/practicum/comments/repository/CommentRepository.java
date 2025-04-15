package ru.practicum.comments.repository;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import ru.practicum.comments.model.Comment;

import java.util.List;
import java.util.Optional;

@Repository
public interface CommentRepository extends JpaRepository<Comment, Long> {

    List<Comment> findAllByAuthorId(Long userId, Pageable pageable);

    Optional<Comment> findByIdAndAuthorId(Long commentId, Long userId);

    List<Comment> findAllByEventId(Long eventId, Pageable pageable);

    List<Comment> findAllByEventId(Long eventId);

    List<Comment> findAllByEventIdIn(List<Long> eventIds);
}
