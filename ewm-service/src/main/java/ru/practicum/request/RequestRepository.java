package ru.practicum.request;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import ru.practicum.request.dto.ConfirmedRequests;
import ru.practicum.request.mapper.ParticipationRequest;
import ru.practicum.request.model.RequestStatus;

import java.util.List;
import java.util.Optional;

public interface RequestRepository extends JpaRepository<ParticipationRequest, Long> {
    Optional<ParticipationRequest> findByIdAndRequesterId(Long requestId, Long userId);

    List<ParticipationRequest> findAllByEventId(Long eventId);

    List<ParticipationRequest> findAllByRequesterId(Long userId);

    List<ParticipationRequest> findAllByEventIdAndIdInAndStatus(Long eventId, List<Long> requestId, RequestStatus status);

    Boolean existsByRequesterIdAndEventId(Long userId, Long eventId);

    long countByEventIdAndStatus(Long eventId, RequestStatus status);

    @Query("SELECT new ru.practicum.request.dto.ConfirmedRequests(COUNT(DISTINCT r.id), r.event.id) " +
            "FROM ParticipationRequest AS r " +
            "WHERE r.event.id IN (:ids) AND r.status = :status " +
            "GROUP BY (r.event)")
    List<ConfirmedRequests> findAllByEventIdInAndStatus(@Param("ids") List<Long> ids, @Param("status") RequestStatus status);
}
