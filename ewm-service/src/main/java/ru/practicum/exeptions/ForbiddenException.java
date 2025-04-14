package ru.practicum.exeptions;

public class ForbiddenException extends RuntimeException {

    public ForbiddenException(String massage) {
        super(massage);
    }
}
