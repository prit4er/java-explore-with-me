package ru.practicum.user;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.practicum.exeptions.ConflictException;
import ru.practicum.exeptions.NotFoundException;
import ru.practicum.user.dto.NewUserRequest;
import ru.practicum.user.dto.UserDto;
import ru.practicum.user.mapper.UserMapper;
import ru.practicum.user.model.User;

import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserServiceImpl implements UserService {

    private final UserRepository userRepository;

    @Override
    @Transactional
    public UserDto create(NewUserRequest newUserRequest) {

        if (!userRepository.findByEmailIgnoreCase(newUserRequest.getEmail()).isEmpty()) {
            throw new ConflictException("Email уже используется");
        }

        return UserMapper.toDto(userRepository.save(UserMapper.toEntity(newUserRequest)));
    }

    @Override
    public List<UserDto> get(List<Long> ids, Integer from, Integer size) {

        Pageable pageable = PageRequest.of(from / size, size);

        if (ids == null) {
            return userRepository.findAll(pageable).stream()
                                 .map(UserMapper::toDto)
                                 .collect(Collectors.toList());
        } else {
            return userRepository.findAllByIdIn(ids, pageable).stream()
                                 .map(UserMapper::toDto)
                                 .collect(Collectors.toList());
        }

    }

    @Override
    @Transactional
    public void delete(Long id) {

        User user = userRepository.findById(id)
                                  .orElseThrow(() -> new NotFoundException("Пользователь с id " + id + " не найден"));

        userRepository.delete(user);
    }
}
