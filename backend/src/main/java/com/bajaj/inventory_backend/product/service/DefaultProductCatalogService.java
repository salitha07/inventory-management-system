package com.bajaj.inventory_backend.product.service;

import com.bajaj.inventory_backend.common.exception.ProductNotFoundException;
import com.bajaj.inventory_backend.product.dto.ProductPriceDetails;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Default implementation of {@link ProductCatalogService}.
 *
 * <p>Serves as the baseline implementation containing canonical Bajaj spare parts
 * until Member 2's database-backed Product Service is integrated. Automatically yields
 * precedence to any custom {@link ProductCatalogService} implementation bean.
 */
@Service
@ConditionalOnMissingBean(value = ProductCatalogService.class, ignored = DefaultProductCatalogService.class)
public class DefaultProductCatalogService implements ProductCatalogService {

    private final ConcurrentMap<Long, ProductPriceDetails> catalog = new ConcurrentHashMap<>();

    public DefaultProductCatalogService() {
        seedCanonicalCatalog();
    }

    private void seedCanonicalCatalog() {
        registerProduct(new ProductPriceDetails(101L, "DH101048", "Clutch Plate Assembly", new BigDecimal("220.00"), true));
        registerProduct(new ProductPriceDetails(205L, "36JZ0001", "Bajaj DTS-i 4T 10W-30 Engine Oil (1L)", new BigDecimal("430.00"), true));
        registerProduct(new ProductPriceDetails(312L, "DJ151034", "Brake Shoe Set Rear", new BigDecimal("250.00"), true));
        registerProduct(new ProductPriceDetails(401L, "DK101235", "Drive Chain Sprocket Kit", new BigDecimal("380.00"), true));
        registerProduct(new ProductPriceDetails(504L, "JN351600", "Alternator Stator Coil", new BigDecimal("950.00"), true));
    }

    public void registerProduct(ProductPriceDetails product) {
        catalog.put(product.productId(), product);
    }

    @Override
    public ProductPriceDetails getProductDetails(Long productId) throws ProductNotFoundException {
        if (productId == null) {
            throw new ProductNotFoundException("Product ID cannot be null");
        }
        ProductPriceDetails details = catalog.get(productId);
        if (details == null || !details.active()) {
            throw new ProductNotFoundException(productId);
        }
        return details;
    }

    @Override
    public BigDecimal getAuthoritativeSellingPrice(Long productId) throws ProductNotFoundException {
        return getProductDetails(productId).sellingPrice();
    }
}

