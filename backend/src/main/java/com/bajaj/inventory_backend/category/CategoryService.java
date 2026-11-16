
package com.bajaj.inventory_backend.category;

import com.bajaj.inventory_backend.dto.category.CategoryRequestDTO;
import com.bajaj.inventory_backend.dto.category.CategoryResponseDTO;
import com.bajaj.inventory_backend.repository.CategoryRepository;
import com.bajaj.inventory_backend.repository.ProductRepository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CategoryService {

    private final CategoryRepository categoryRepository;
    private final ProductRepository productRepository;

    public CategoryService(
            CategoryRepository categoryRepository,
            ProductRepository productRepository) {
        this.categoryRepository = categoryRepository;
        this.productRepository = productRepository;
    }

    @Transactional(readOnly = true)
    public Page<CategoryResponseDTO> getAllCategories(Pageable pageable) {
        return categoryRepository.findAll(pageable)
                .map(this::convertToResponseDTO);
    }

    @Transactional(readOnly = true)
    public CategoryResponseDTO getCategoryById(Long id) {
        Category category = findCategoryById(id);
        return convertToResponseDTO(category);
    }

    @Transactional
    public CategoryResponseDTO createCategory(CategoryRequestDTO request) {

        String categoryName = request.getCategoryName().trim();

        if (categoryRepository.existsByCategoryNameIgnoreCase(categoryName)) {
            throw new IllegalArgumentException(
                    "Category name already exists");
        }

        Category category = new Category();
        category.setCategoryName(categoryName);
        category.setDescription(request.getDescription());

        Category savedCategory = categoryRepository.save(category);

        return convertToResponseDTO(savedCategory);
    }

    @Transactional
    public CategoryResponseDTO updateCategory(
            Long id,
            CategoryRequestDTO request) {

        Category category = findCategoryById(id);

        String categoryName = request.getCategoryName().trim();

        if (categoryRepository.existsByCategoryNameIgnoreCaseAndIdNot(
                categoryName, id)) {
            throw new IllegalArgumentException(
                    "Category name already exists");
        }

        category.setCategoryName(categoryName);
        category.setDescription(request.getDescription());

        Category updatedCategory = categoryRepository.save(category);

        return convertToResponseDTO(updatedCategory);
    }

    @Transactional
    public CategoryResponseDTO updateCategoryStatus(
            Long id,
            boolean active) {

        Category category = findCategoryById(id);

        if (!active && category.isActive()
                && productRepository.existsByCategory_IdAndActiveTrue(id)) {
            throw new IllegalStateException(
                    "Cannot deactivate category because active products are assigned to it");
        }

        category.setActive(active);

        Category updatedCategory = categoryRepository.save(category);

        return convertToResponseDTO(updatedCategory);
    }

    private Category findCategoryById(Long id) {
        return categoryRepository.findById(id)
                .orElseThrow(() ->
                        new IllegalArgumentException(
                                "Category not found with ID: " + id));
    }

    private CategoryResponseDTO convertToResponseDTO(Category category) {
        return new CategoryResponseDTO(
                category.getId(),
                category.getCategoryName(),
                category.getDescription(),
                category.isActive(),
                category.getCreatedAt(),
                category.getUpdatedAt()
        );
    }
}
