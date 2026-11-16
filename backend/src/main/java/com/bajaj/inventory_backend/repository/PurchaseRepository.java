
package com.bajaj.inventory_backend.purchase;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface PurchaseRepository
        extends JpaRepository<Purchase, Long> {

    Optional<Purchase> findByPurchaseNumber(String purchaseNumber);
}
