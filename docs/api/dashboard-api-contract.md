
# Dashboard API Contract

## Base URL

`/api/v1/dashboard`

All endpoints use GET requests.

## 1. Dashboard Summary

**Endpoint:** `GET /api/v1/dashboard/summary`

**Success:** `200 OK`

### Response Example

```json
{
  "todaySales": 12500.00,
  "monthlySales": 185000.00,
  "purchaseSummary": null,
  "totalProducts": 150,
  "lowStockCount": null
}
```

| Field | Type | Description |
|---|---|---|
| todaySales | BigDecimal | Today's completed sales |
| monthlySales | BigDecimal | Current month's completed sales |
| purchaseSummary | BigDecimal / null | Purchase summary |
| totalProducts | Long | Total products |
| lowStockCount | Long / null | Low stock count |

Purchase summary and low-stock count are currently unavailable because their required database migrations are not active.

## 2. Low Stock Alerts

**Endpoint:** `GET /api/v1/dashboard/low-stock`

**Current Status:** `503 Service Unavailable`

**Current Error Message:**

`Low-stock alerts are unavailable because the inventory migration is not active.`

### Planned Response Structure

| Field | Type | Description |
|---|---|---|
| productId | Long | Product identifier |
| productName | String | Product name |
| sku | String | Product SKU |
| currentQuantity | Integer | Current stock |
| reorderLevel | Integer | Reorder level |

The planned response is not currently available.

## 3. Recent Activity

**Endpoint:** `GET /api/v1/dashboard/recent-activity`

**Success:** `200 OK`

Returns up to 10 recent sales records, ordered by activity date descending.

### Response Example

```json
[
  {
    "id": 25,
    "activityType": "SALE",
    "referenceNumber": "SAL-025",
    "description": "Sale status: COMPLETED",
    "amount": 12500.00,
    "activityDate": "2026-11-21T08:30:00Z"
  }
]
```

| Field | Type | Description |
|---|---|---|
| id | Long | Activity identifier |
| activityType | String | Activity type |
| referenceNumber | String | Sale reference |
| description | String | Activity description |
| amount | BigDecimal | Sale amount |
| activityDate | Instant | Activity date and time |

## Current Implementation Status

| Feature | Status |
|---|---|
| Today's sales | Available |
| Monthly sales | Available |
| Total products | Available |
| Purchase summary | Unavailable |
| Low-stock count | Unavailable |
| Low-stock alerts | Temporarily unavailable (503) |
| Recent sales | Available |
| Recent purchases | Pending dependency |
| Recent stock movements | Pending dependency |

## Dependencies

- Purchase migration is required for purchase summary and recent purchases.
- Inventory migration is required for low-stock alerts and stock movements.

These migrations belong to their respective modules and are not modified by Member 9.

**Module:** Member 9 - Dashboard & Alerts API
