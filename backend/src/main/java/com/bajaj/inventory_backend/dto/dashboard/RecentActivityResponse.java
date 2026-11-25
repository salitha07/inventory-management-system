
package com.bajaj.inventory_backend.dto.dashboard;

import java.math.BigDecimal;
import java.time.Instant;

public class RecentActivityResponse {

    private Long id;
    private String activityType;
    private String referenceNumber;
    private String description;
    private BigDecimal amount;
    private Instant activityDate;

    public RecentActivityResponse(
            Long id,
            String activityType,
            String referenceNumber,
            String description,
            BigDecimal amount,
            Instant activityDate) {

        this.id = id;
        this.activityType = activityType;
        this.referenceNumber = referenceNumber;
        this.description = description;
        this.amount = amount;
        this.activityDate = activityDate;
    }

    public Long getId() {
        return id;
    }

    public String getActivityType() {
        return activityType;
    }

    public String getReferenceNumber() {
        return referenceNumber;
    }

    public String getDescription() {
        return description;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public Instant getActivityDate() {
        return activityDate;
    }
}
