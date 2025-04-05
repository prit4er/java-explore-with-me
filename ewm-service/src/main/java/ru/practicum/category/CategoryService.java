package ru.practicum.category;

import ru.practicum.category.dto.CategoryDto;
import ru.practicum.category.dto.CategoryRequest;

import java.util.List;

public interface CategoryService {

    CategoryDto create(CategoryRequest categoryRequest);
    CategoryDto update(Long id, CategoryRequest categoryRequest);
    void delete(Long id);
    List<CategoryDto> getCategories(Integer from, Integer size);
    CategoryDto getCategoryById(Long categoryId);
}
