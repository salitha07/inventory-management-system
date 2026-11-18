
package com.bajaj.inventory_backend.product;

import com.bajaj.inventory_backend.category.Category;
import com.bajaj.inventory_backend.dto.product.ProductRequestDTO;
import com.bajaj.inventory_backend.dto.product.ProductResponseDTO;
import com.bajaj.inventory_backend.repository.CategoryRepository;
import com.bajaj.inventory_backend.repository.ProductRepository;
import com.bajaj.inventory_backend.common.exception.DuplicateResourceException;
import com.bajaj.inventory_backend.common.exception.ResourceNotFoundException;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProductService {

    private final ProductRepository productRepository;
    private final CategoryRepository categoryRepository;

    public ProductService(
            ProductRepository productRepository,
            CategoryRepository categoryRepository) {
        this.productRepository = productRepository;
        this.categoryRepository = categoryRepository;
    }

    @Transactional(readOnly = true)
    public Page<ProductResponseDTO> getAllProducts(
            String search,
            Long categoryId,
            Boolean active,
            Pageable pageable) {

        Specification<Product> specification =
        (root, query, cb) -> cb.conjunction();

        if (search != null && !search.isBlank()) {
            String keyword = "%" + search.trim().toLowerCase() + "%";

            specification = specification.and((root, query, cb) ->
                    cb.or(
                            cb.like(cb.lower(root.get("productName")), keyword),
                            cb.like(cb.lower(root.get("productCode")), keyword)
                    )
            );
        }

        if (categoryId != null) {
            specification = specification.and((root, query, cb) ->
                    cb.equal(root.get("category").get("id"), categoryId)
            );
        }

        if (active != null) {
            specification = specification.and((root, query, cb) ->
                    cb.equal(root.get("active"), active)
            );
        }

        return productRepository.findAll(specification, pageable)
                .map(this::convertToResponseDTO);
    }

    @Transactional(readOnly = true)
    public ProductResponseDTO getProductById(Long id) {
        Product product = findProductById(id);
        return convertToResponseDTO(product);
    }

    @Transactional
    public ProductResponseDTO createProduct(ProductRequestDTO request) {

        String productCode = request.getProductCode().trim();

        if (productRepository.existsByProductCodeIgnoreCase(productCode)) {
            throw new DuplicateResourceException(
                    "Product code already exists: " + productCode
            );
        }

        Category category = findActiveCategory(request.getCategoryId());

        Product product = new Product();
        product.setProductCode(productCode);
        product.setProductName(request.getProductName().trim());
        product.setDescription(request.getDescription());
        product.setCategory(category);
        product.setCostPrice(request.getCostPrice());
        product.setSellingPrice(request.getSellingPrice());

        Product savedProduct = productRepository.save(product);

        return convertToResponseDTO(savedProduct);
    }

    @Transactional
    public ProductResponseDTO updateProduct(
            Long id,
            ProductRequestDTO request) {

        Product product = findProductById(id);

        String productCode = request.getProductCode().trim();

        if (productRepository.existsByProductCodeIgnoreCaseAndIdNot(
                productCode, id)) {
            throw new DuplicateResourceException(
                    "Product code already exists: " + productCode
            );
        }

        Category category = findActiveCategory(request.getCategoryId());

        product.setProductCode(productCode);
        product.setProductName(request.getProductName().trim());
        product.setDescription(request.getDescription());
        product.setCategory(category);
        product.setCostPrice(request.getCostPrice());
        product.setSellingPrice(request.getSellingPrice());

        Product updatedProduct = productRepository.save(product);

        return convertToResponseDTO(updatedProduct);
    }

    @Transactional
    public ProductResponseDTO updateProductStatus(
            Long id,
            boolean active) {

        Product product = findProductById(id);
        product.setActive(active);

        Product updatedProduct = productRepository.save(product);

        return convertToResponseDTO(updatedProduct);
    }

    private Product findProductById(Long id) {
        return productRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Product not found with ID: " + id
                ));
    }

    private Category findActiveCategory(Long categoryId) {
        return categoryRepository.findById(categoryId)
                .filter(Category::isActive)
                .orElseThrow(() ->
                        new ResourceNotFoundException(
                                "Category not found or inactive with ID: "
                                        + categoryId));
    }

    private ProductResponseDTO convertToResponseDTO(Product product) {
        return new ProductResponseDTO(
                product.getId(),
                product.getProductCode(),
                product.getProductName(),
                product.getDescription(),
                product.getCategory().getId(),
                product.getCategory().getCategoryName(),
                product.getCostPrice(),
                product.getSellingPrice(),
                product.isActive(),
                product.getCreatedAt(),
                product.getUpdatedAt()
        );
    }
}
