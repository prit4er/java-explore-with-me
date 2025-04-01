package ru.practicum;

import lombok.Getter;
import lombok.Setter;
import org.jetbrains.annotations.NotNull;

@Getter
@Setter
public class HitRequest {

    @NotNull
    private String app;
    private String uri;
    @NotNull
    private String ip;
    @NotNull
    private String timestamp;
}
