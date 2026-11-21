
# Purchase Management API Contract

Base URL: /api/v1/purchases

## Endpoints

GET /api/v1/purchases
- Search and list purchases
- Filters: date, supplierId, status

GET /api/v1/purchases/{id}
- View purchase details

POST /api/v1/purchases
- Create a purchase with multiple items
- Initial status: DRAFT
- Does not update stock

POST /api/v1/purchases/{id}/receive
- Receive a purchase
- Changes status to RECEIVED
- Calls InventoryService.receiveStock
- Must execute transactionally

POST /api/v1/purchases/{id}/cancel
- Cancel a DRAFT purchase
- Cannot cancel a RECEIVED purchase directly

## Inventory Rules

- Only InventoryService updates stock balances.
- Stock increases only when a purchase is received.
- Receiving must be atomic.
- Each stock movement records the purchase reference.

## Roles

Proposed access: ADMIN, MANAGER, INVENTORY_STAFF.
Final permissions must follow the team's role matrix.
```
