
package com.bajaj.inventory_backend.dto.category;

import jakarta.validation.constraints.NotNull;

public class CategoryStatusRequest {

    @NotNull(message = "Active status is required")
    private Boolean active;

    public CategoryStatusRequest() {
    }

    public Boolean getActive() {
        return active;
    }

    public void setActive(Boolean active) {
        this.active = active;
    }
}
