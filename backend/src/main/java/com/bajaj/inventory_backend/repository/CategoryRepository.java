
package com.bajaj.inventory_backend.repository;

import com.bajaj.inventory_backend.category.Category;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface CategoryRepository extends JpaRepository<Category, Long> {

    boolean existsByCategoryNameIgnoreCase(String categoryName);

    Optional<Category> findByCategoryNameIgnoreCase(String categoryName);

    boolean existsByCategoryNameIgnoreCaseAndIdNot(String categoryName, Long id);

    boolean existsByIdAndActiveTrue(Long id);
}
