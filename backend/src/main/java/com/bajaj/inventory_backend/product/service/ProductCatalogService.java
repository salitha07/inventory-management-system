package com.bajaj.inventory_backend.product.service;

import com.bajaj.inventory_backend.common.exception.ProductNotFoundException;
import com.bajaj.inventory_backend.product.dto.ProductPriceDetails;

import java.math.BigDecimal;

/**
 * Service contract for querying the authoritative product catalog (Member 2).
 *
 * <p>Enforces zero-trust price resolution by retrieving genuine catalog selling prices
 * directly from the database and validating catalog active status.
 */
public interface ProductCatalogService {

    /**
     * Retrieves authoritative product metadata and selling price.
     *
     * @param productId product primary key
     * @return catalog pricing and identification details
     * @throws ProductNotFoundException if product does not exist or is inactive
     */
    ProductPriceDetails getProductDetails(Long productId) throws ProductNotFoundException;

    /**
     * Retrieves authoritative selling price for a product.
     *
     * @param productId product primary key
     * @return catalog unit selling price
     * @throws ProductNotFoundException if product does not exist or is inactive
     */
    BigDecimal getAuthoritativeSellingPrice(Long productId) throws ProductNotFoundException;
}

