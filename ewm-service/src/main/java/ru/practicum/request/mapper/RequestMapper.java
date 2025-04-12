package ru.practicum.request.mapper;

import lombok.experimental.UtilityClass;
import ru.practicum.request.dto.ParticipationRequest;
import ru.practicum.request.dto.RequestDto;

@UtilityClass
public class RequestMapper {

    public RequestDto toDto(ParticipationRequest participationRequest) {
        return new RequestDto(
                participationRequest.getId(),
                participationRequest.getCreated(),
                participationRequest.getEvent().getId(),
                participationRequest.getRequester().getId(),
                participationRequest.getStatus()
        );
    }
}
