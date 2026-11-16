
package com.bajaj.inventory_backend.repository;

import com.bajaj.inventory_backend.product.Product;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ProductRepository extends JpaRepository<Product, Long> {

    boolean existsByProductCodeIgnoreCase(String productCode);

    Optional<Product> findByProductCodeIgnoreCase(String productCode);

    boolean existsByProductCodeIgnoreCaseAndIdNot(String productCode, Long id);

    boolean existsByCategory_IdAndActiveTrue(Long categoryId);
}
