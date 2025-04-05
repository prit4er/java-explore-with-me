package ru.practicum.category.mapper;

import org.springframework.stereotype.Component;
import ru.practicum.category.dto.CategoryDto;
import ru.practicum.category.dto.CategoryRequest;
import ru.practicum.category.model.Category;

@Component
public class CategoryMapper {

    public static Category requestToEntity(CategoryRequest request) {
        Category category = new Category();
        category.setName(request.getName());
        return category;
    }

    public static Category requestToEntity(Long id, CategoryRequest request) {
        Category category = new Category();
        category.setId(id);
        category.setName(request.getName());
        return category;
    }

    public static CategoryDto toDto(Category category) {
        return new CategoryDto(category.getId(), category.getName());
    }

}
