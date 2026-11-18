
package com.bajaj.inventory_backend.dto.product;

import jakarta.validation.constraints.NotNull;

public class ProductStatusRequest {

    @NotNull(message = "Active status is required")
    private Boolean active;

    public ProductStatusRequest() {
    }

    public Boolean getActive() {
        return active;
    }

    public void setActive(Boolean active) {
        this.active = active;
    }
}
