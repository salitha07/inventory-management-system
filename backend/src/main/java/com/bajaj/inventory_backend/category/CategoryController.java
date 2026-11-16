
package com.bajaj.inventory_backend.category;

import com.bajaj.inventory_backend.common.response.ApiResponse;
import com.bajaj.inventory_backend.dto.category.CategoryRequestDTO;
import com.bajaj.inventory_backend.dto.category.CategoryResponseDTO;
import com.bajaj.inventory_backend.dto.category.CategoryStatusRequest;

import jakarta.validation.Valid;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/categories")
public class CategoryController {

    private final CategoryService categoryService;

    public CategoryController(CategoryService categoryService) {
        this.categoryService = categoryService;
    }

    // Get all categories with pagination
    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'CASHIER', 'INVENTORY_STAFF')")
    public ResponseEntity<ApiResponse<Page<CategoryResponseDTO>>> getAllCategories(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {

        if (page < 0 || size < 1 || size > 100) {
            throw new IllegalArgumentException(
                    "Invalid pagination values");
        }

        Pageable pageable = PageRequest.of(
                page,
                size,
                Sort.by("categoryName").ascending()
        );

        Page<CategoryResponseDTO> categories =
                categoryService.getAllCategories(pageable);

        return ResponseEntity.ok(
                ApiResponse.success(
                        "Categories retrieved successfully",
                        categories
                )
        );
    }

    // Get category by ID
    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'CASHIER', 'INVENTORY_STAFF')")
    public ResponseEntity<ApiResponse<CategoryResponseDTO>> getCategoryById(
            @PathVariable Long id) {

        CategoryResponseDTO category =
                categoryService.getCategoryById(id);

        return ResponseEntity.ok(
                ApiResponse.success(
                        "Category retrieved successfully",
                        category
                )
        );
    }

    // Create category
    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public ResponseEntity<ApiResponse<CategoryResponseDTO>> createCategory(
            @Valid @RequestBody CategoryRequestDTO request) {

        CategoryResponseDTO category =
                categoryService.createCategory(request);

        return ResponseEntity.status(HttpStatus.CREATED).body(
                ApiResponse.success(
                        "Category created successfully",
                        category
                )
        );
    }

    // Update category
    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public ResponseEntity<ApiResponse<CategoryResponseDTO>> updateCategory(
            @PathVariable Long id,
            @Valid @RequestBody CategoryRequestDTO request) {

        CategoryResponseDTO category =
                categoryService.updateCategory(id, request);

        return ResponseEntity.ok(
                ApiResponse.success(
                        "Category updated successfully",
                        category
                )
        );
    }

    // Activate or deactivate category
    @PatchMapping("/{id}/status")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public ResponseEntity<ApiResponse<CategoryResponseDTO>> updateCategoryStatus(
            @PathVariable Long id,
            @Valid @RequestBody CategoryStatusRequest request) {

        CategoryResponseDTO category =
                categoryService.updateCategoryStatus(
                        id,
                        request.getActive()
                );

        return ResponseEntity.ok(
                ApiResponse.success(
                        "Category status updated successfully",
                        category
                )
        );
    }
}
