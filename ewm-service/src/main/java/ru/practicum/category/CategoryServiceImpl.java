package ru.practicum.category;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import ru.practicum.category.dto.CategoryDto;
import ru.practicum.category.dto.CategoryRequest;
import ru.practicum.category.mapper.CategoryMapper;
import ru.practicum.category.model.Category;
import ru.practicum.exeptions.ConflictException;
import ru.practicum.exeptions.NotFoundException;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CategoryServiceImpl implements CategoryService {

    private final CategoryRepository categoryRepository;

    @Override
    public CategoryDto create(CategoryRequest categoryRequest) {
        checkNameUnique(categoryRequest.getName());

        Category category = new Category();
        category.setName(categoryRequest.getName());

        Category saved = categoryRepository.save(category);
        return CategoryMapper.toDto(saved);
    }


    @Override
    public CategoryDto update(Long id, CategoryRequest categoryRequest) {
        Category category = categoryRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Категория не найдена: id = " + id));

        if (!category.getName().equalsIgnoreCase(categoryRequest.getName())) {
            checkNameUnique(categoryRequest.getName());
        }

        category.setName(categoryRequest.getName());
        Category updated = categoryRepository.save(category);
        return CategoryMapper.toDto(updated);
    }

    @Override
    public void delete(Long id) {
        if (!categoryRepository.existsById(id)) {
            throw new NotFoundException("Категория не найдена: id = " + id);
        }
        categoryRepository.deleteById(id);
    }

    @Override
    public List<CategoryDto> getCategories(Integer from, Integer size) {
        return categoryRepository.findAll(PageRequest.of(from / size, size)).stream()
                                 .map(CategoryMapper::toDto)
                                 .collect(Collectors.toList());
    }

    @Override
    public CategoryDto getCategoryById(Long categoryId) {
        return categoryRepository.findById(categoryId)
                                 .map(CategoryMapper::toDto)
                                 .orElseThrow(() -> new NotFoundException("Category with id=" + categoryId + " was not found"));
    }

    private void checkNameUnique(String name) {
        if (!categoryRepository.findByNameIgnoreCase(name).isEmpty()) {
            throw new ConflictException("Категория с именем '" + name + "' уже существует");
        }
    }
}
