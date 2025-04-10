package ru.practicum.locations.mapper;

import lombok.experimental.UtilityClass;
import ru.practicum.locations.dto.LocationDto;
import ru.practicum.locations.model.Location;

@UtilityClass
public class LocationMapper {
    public Location mapToLocation(LocationDto locationDto) {
        return new Location(locationDto.getLat(), locationDto.getLon());
    }

    public LocationDto mapToLocationDto(Location location) {
        return new LocationDto(location.getLat(), location.getLon());
    }
}
