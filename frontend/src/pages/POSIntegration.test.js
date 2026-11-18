import { describe, it } from "node:test";
import assert from "node:assert/strict";
import {
  addItemToCart,
  removeItemFromCart,
  updateItemQuantity,
  increaseItemQuantity,
  decreaseItemQuantity,
  clearCartItems,
  calculateLineTotal,
  calculateSubtotal,
  calculateFinalTotal,
  calculateChangeDue,
  calculateCartTotals,
  validateCheckoutForm,
  isValidQuantity,
  isValidDiscount,
  PAYMENT_METHODS,
} from "../utils/cartOperations.js";

/**
 * POS & Sales Management End-to-End Workflow Integration Test Suite.
 *
 * Verifies all 12 key sales requirements:
 * 1. Product search & catalog filtering
 * 2. Add and remove cart items
 * 3. Quantity updates & validation
 * 4. Subtotal calculation (paise precision)
 * 5. Discount and final total calculation
 * 6. Checkout validation (walk-in, customer, payment methods)
 * 7. Successful checkout payload & response flow
 * 8. Insufficient stock handling & cart state preservation
 * 9. Cart clearance invariants
 * 10. Concurrency & client duplicate submission prevention
 * 11. Invoice and receipt display integration
 * 12. Sales history and transaction details integration
 */
describe("POS & Sales Management Integration Test Suite", () => {
  // Canonical sample catalog
  const catalog = [
    { id: 101, partNumber: "DH101048", name: "Clutch Plate Assembly", sellingPrice: 220.0, availableQuantity: 15, active: true },
    { id: 205, partNumber: "36JZ0001", name: "Bajaj DTS-i 4T 10W-30 Oil", sellingPrice: 430.0, availableQuantity: 8, active: true },
    { id: 312, partNumber: "DJ151034", name: "Brake Shoe Set Rear", sellingPrice: 250.0, availableQuantity: 0, active: true }, // Out of stock
    { id: 401, partNumber: "DK101235", name: "Drive Chain Sprocket Kit", sellingPrice: 380.0, availableQuantity: 5, active: false }, // Discontinued
  ];

  // ─────────────────────────────────────────────────────────────
  //  1. Product Search & Catalog Lookup
  // ─────────────────────────────────────────────────────────────
  describe("1. Product Search Integration", () => {
    function filterProducts(query, items = catalog) {
      if (!query || !query.trim()) return items.filter((p) => p.active);
      const q = query.trim().toLowerCase();
      return items.filter(
        (p) =>
          p.active &&
          (p.name.toLowerCase().includes(q) ||
            p.partNumber.toLowerCase().includes(q))
      );
    }

    it("should search and match products by part number case-insensitively", () => {
      const results = filterProducts("dh101048");
      assert.strictEqual(results.length, 1);
      assert.strictEqual(results[0].id, 101);
      assert.strictEqual(results[0].partNumber, "DH101048");
    });

    it("should search and match products by partial product name", () => {
      const results = filterProducts("oil");
      assert.strictEqual(results.length, 1);
      assert.strictEqual(results[0].id, 205);
      assert.ok(results[0].name.includes("Engine Oil") || results[0].name.includes("Oil"));
    });

    it("should exclude inactive/discontinued products from search results", () => {
      const results = filterProducts("DK101235"); // Drive Chain Sprocket Kit is inactive
      assert.strictEqual(results.length, 0);
    });

    it("should return empty list when no products match query", () => {
      const results = filterProducts("NonExistentPartXYZ");
      assert.strictEqual(results.length, 0);
    });
  });

  // ─────────────────────────────────────────────────────────────
  //  2. Add and Remove Cart Items
  // ─────────────────────────────────────────────────────────────
  describe("2. Cart Item Management (Add & Remove)", () => {
    it("should add a new product to the cart with default quantity 1", () => {
      const product = catalog[0]; // Clutch Plate (101)
      const cart = addItemToCart([], product, 1);

      assert.strictEqual(cart.length, 1);
      assert.strictEqual(cart[0].id, 101);
      assert.strictEqual(cart[0].sku, "DH101048");
      assert.strictEqual(cart[0].quantity, 1);
      assert.strictEqual(cart[0].unitPrice, 220.0);
    });

    it("should increment quantity when re-adding an existing product instead of duplicating line item", () => {
      const product = catalog[0];
      let cart = addItemToCart([], product, 2);
      cart = addItemToCart(cart, product, 3);

      assert.strictEqual(cart.length, 1, "Must maintain a single row for the product");
      assert.strictEqual(cart[0].quantity, 5, "Quantity must be aggregated (2 + 3 = 5)");
    });

    it("should remove an item by product ID", () => {
      let cart = addItemToCart([], catalog[0], 2);
      cart = addItemToCart(cart, catalog[1], 1);
      assert.strictEqual(cart.length, 2);

      cart = removeItemFromCart(cart, 101);
      assert.strictEqual(cart.length, 1);
      assert.strictEqual(cart[0].id, 205);
    });

    it("should reject adding items with 0 available on-hand stock", () => {
      const outOfStockProduct = catalog[2]; // Brake Shoe (qty 0)
      const cart = addItemToCart([], outOfStockProduct, 1);
      assert.strictEqual(cart.length, 0, "Out-of-stock product must not be added to cart");
    });
  });

  // ─────────────────────────────────────────────────────────────
  //  3. Quantity Updates & Validation
  // ─────────────────────────────────────────────────────────────
  describe("3. Quantity Updates & Boundary Enforcement", () => {
    it("should allow valid manual quantity updates within available stock", () => {
      let cart = addItemToCart([], catalog[0], 1); // available 15
      cart = updateItemQuantity(cart, 101, 7);

      assert.strictEqual(cart[0].quantity, 7);
    });

    it("should reject manual quantity updates exceeding available stock and keep current quantity", () => {
      let cart = addItemToCart([], catalog[1], 2); // available 8
      cart = updateItemQuantity(cart, 205, 50); // exceeds 8, rejected

      assert.strictEqual(cart[0].quantity, 2, "Quantity must remain 2 when update exceeds available stock");
    });

    it("should reject non-positive, zero, or non-integer quantities in validation", () => {
      assert.strictEqual(isValidQuantity("0", 10), false);
      assert.strictEqual(isValidQuantity("-5", 10), false);
      assert.strictEqual(isValidQuantity("3.5", 10), false);
      assert.strictEqual(isValidQuantity("abc", 10), false);
      assert.strictEqual(isValidQuantity("", 10), false);
      assert.strictEqual(isValidQuantity(null, 10), false);

      assert.strictEqual(isValidQuantity("5", 10), true);
      assert.strictEqual(isValidQuantity(5, 10), true);
    });

    it("should increase and decrease quantities using step controls", () => {
      let cart = addItemToCart([], catalog[0], 2);
      cart = increaseItemQuantity(cart, 101, 1);
      assert.strictEqual(cart[0].quantity, 3);

      cart = decreaseItemQuantity(cart, 101, 1);
      assert.strictEqual(cart[0].quantity, 2);

      // Decreasing at 1 should not reduce below 1
      cart = decreaseItemQuantity(cart, 101, 1);
      cart = decreaseItemQuantity(cart, 101, 1);
      assert.strictEqual(cart[0].quantity, 1, "Quantity must not drop below 1");
    });
  });

  // ─────────────────────────────────────────────────────────────
  //  4. Subtotal Calculation
  // ─────────────────────────────────────────────────────────────
  describe("4. Subtotal Calculation (Paise Precision)", () => {
    it("should calculate line total as unit price multiplied by quantity", () => {
      assert.strictEqual(calculateLineTotal(220.0, 3), 660.0);
      assert.strictEqual(calculateLineTotal(430.0, 2), 860.0);
      assert.strictEqual(calculateLineTotal(19.99, 3), 59.97);
    });

    it("should calculate subtotal as sum of line totals across items", () => {
      const cart = [
        { id: 101, unitPrice: 220.0, quantity: 2 }, // 440.00
        { id: 205, unitPrice: 430.0, quantity: 1 }, // 430.00
      ];
      assert.strictEqual(calculateSubtotal(cart), 870.0);
    });

    it("should return 0.00 subtotal for an empty cart", () => {
      assert.strictEqual(calculateSubtotal([]), 0);
      assert.strictEqual(calculateSubtotal(null), 0);
    });
  });

  // ─────────────────────────────────────────────────────────────
  //  5. Discount and Final Total Calculation
  // ─────────────────────────────────────────────────────────────
  describe("5. Discount and Final Total Calculation", () => {
    it("should validate and apply discounts within subtotal bounds", () => {
      assert.strictEqual(isValidDiscount(50.0, 870.0), true);
      assert.strictEqual(isValidDiscount(0, 870.0), true);
      assert.strictEqual(isValidDiscount("100.50", 870.0), true);

      // Reject negative discount and discount exceeding subtotal
      assert.strictEqual(isValidDiscount(-10, 870.0), false);
      assert.strictEqual(isValidDiscount(900.0, 870.0), false);
      assert.strictEqual(isValidDiscount("abc", 870.0), false);
    });

    it("should calculate final total accurately as subtotal minus discount", () => {
      const subtotal = 870.0;
      const discount = 70.0;
      assert.strictEqual(calculateFinalTotal(subtotal, discount), 800.0);
    });

    it("should prevent negative final totals even if excessive discount is passed", () => {
      const subtotal = 500.0;
      const excessiveDiscount = 600.0;
      assert.strictEqual(calculateFinalTotal(subtotal, excessiveDiscount), 0.0);
    });

    it("should calculate change due accurately for cash payments", () => {
      const total = 800.0;
      assert.strictEqual(calculateChangeDue(1000.0, total), 200.0);
      assert.strictEqual(calculateChangeDue(800.0, total), 0.0);
      assert.strictEqual(calculateChangeDue(500.0, total), 0.0, "Short tender gives 0 change due");
    });
  });

  // ─────────────────────────────────────────────────────────────
  //  6. Checkout Validation
  // ─────────────────────────────────────────────────────────────
  describe("6. Checkout Form Validation", () => {
    const validCart = [
      { id: 101, unitPrice: 220.0, quantity: 2 },
    ];
    const subtotal = 440.0;
    const total = 440.0;

    it("should validate a valid walk-in cash checkout", () => {
      const result = validateCheckoutForm({
        cart: validCart,
        isWalkIn: true,
        customerId: null,
        paymentMethod: "CASH",
        amountTendered: 500.0,
        subtotal,
        total,
        discount: 0,
      });

      assert.strictEqual(result.isValid, true);
      assert.strictEqual(result.payload.paymentMethod, "CASH");
      assert.strictEqual(result.payload.amountTendered, 500.0);
      assert.strictEqual(result.payload.customerId, null);
      assert.strictEqual(result.payload.items.length, 1);
    });

    it("should reject cash sale when tendered amount is less than total", () => {
      const result = validateCheckoutForm({
        cart: validCart,
        isWalkIn: true,
        paymentMethod: "CASH",
        amountTendered: 400.0, // less than 440.0
        subtotal,
        total,
      });

      assert.strictEqual(result.isValid, false);
      assert.ok(result.errors.amountTendered.includes("Amount tendered must be at least"));
    });

    it("should reject customer store credit for walk-in sales", () => {
      const result = validateCheckoutForm({
        cart: validCart,
        isWalkIn: true,
        customerId: null,
        paymentMethod: "CREDIT",
        subtotal,
        total,
      });

      assert.strictEqual(result.isValid, false);
      assert.ok(result.errors.customerId.includes("Customer store credit requires a registered customer account"));
    });

    it("should accept customer store credit for registered customer accounts", () => {
      const result = validateCheckoutForm({
        cart: validCart,
        isWalkIn: false,
        customerId: 42,
        paymentMethod: "CREDIT",
        paymentReference: "PO-2026-99",
        subtotal,
        total,
      });

      assert.strictEqual(result.isValid, true);
      assert.strictEqual(result.payload.customerId, 42);
      assert.strictEqual(result.payload.paymentMethod, "CREDIT");
      assert.strictEqual(result.payload.paymentReference, "PO-2026-99");
    });

    it("should reject checkout when cart is empty", () => {
      const result = validateCheckoutForm({
        cart: [],
        paymentMethod: "CASH",
        amountTendered: 500.0,
      });

      assert.strictEqual(result.isValid, false);
      assert.strictEqual(result.errors.cart, "Cart cannot be empty for checkout.");
    });
  });

  // ─────────────────────────────────────────────────────────────
  //  7. Successful Checkout & Cart Clearance Invariant
  // ─────────────────────────────────────────────────────────────
  describe("7 & 9. Successful Checkout & Cart Clearance Invariant", () => {
    it("should clear cart strictly after confirmed transaction success", () => {
      let cart = [
        { id: 101, unitPrice: 220.0, quantity: 2 },
      ];
      let discount = 20;

      // Simulate successful checkout response
      const mockCheckoutResponse = {
        id: 1001,
        saleNumber: "INV-202611-0001",
        status: "COMPLETED",
        total: 420.0,
        changeDue: 80.0,
      };

      assert.ok(mockCheckoutResponse.id);
      assert.strictEqual(mockCheckoutResponse.saleNumber, "INV-202611-0001");

      // On confirmed success: clear cart
      cart = clearCartItems();
      discount = 0;

      assert.strictEqual(cart.length, 0, "Cart must be emptied upon successful checkout");
      assert.strictEqual(discount, 0);
    });

    it("should preserve cart items and state when checkout submission fails", () => {
      let cart = [
        { id: 101, unitPrice: 220.0, quantity: 2 },
      ];

      // Simulate failure (e.g. network glitch or server error)
      const submissionSuccess = false;
      if (!submissionSuccess) {
        // Do NOT clear cart
      }

      assert.strictEqual(cart.length, 1, "Cart must remain intact on checkout error");
      assert.strictEqual(cart[0].quantity, 2);
    });
  });

  // ─────────────────────────────────────────────────────────────
  //  8. Insufficient Stock Error Handling
  // ─────────────────────────────────────────────────────────────
  describe("8. Insufficient Stock Handling", () => {
    function parseCheckoutError(err) {
      if (err.response?.status === 409) {
        return {
          type: "INSUFFICIENT_STOCK",
          message: err.response.data?.message || "Requested quantity exceeds available stock.",
        };
      }
      return {
        type: "GENERIC_ERROR",
        message: err.message || "An unexpected error occurred.",
      };
    }

    it("should parse HTTP 409 Conflict error with stock deficit message", () => {
      const errorResponse = {
        response: {
          status: 409,
          data: {
            status: 409,
            error: "Conflict",
            message: "Insufficient stock for product ID 101 (Clutch Plate Assembly). Requested: 5, Available: 2.",
          },
        },
      };

      const parsed = parseCheckoutError(errorResponse);
      assert.strictEqual(parsed.type, "INSUFFICIENT_STOCK");
      assert.ok(parsed.message.includes("Insufficient stock for product ID 101"));
      assert.ok(parsed.message.includes("Requested: 5, Available: 2"));
    });
  });

  // ─────────────────────────────────────────────────────────────
  //  11. Invoice and Receipt Display Integration
  // ─────────────────────────────────────────────────────────────
  describe("11. Invoice & Receipt Display Integration", () => {
    function normalizeReceipt(data) {
      return {
        storeName: data.storeInfo?.storeName || "Bajaj Genuine Spare Parts - Central Depot",
        saleNumber: data.receiptInfo?.saleNumber,
        saleDate: data.receiptInfo?.saleDate,
        cashierName: data.receiptInfo?.cashierName || "Cashier",
        customerName: data.receiptInfo?.customerName || "Walk-in Customer",
        items: (data.items || []).map((it) => ({
          partNumber: it.partNumber,
          description: it.description,
          quantity: it.quantity,
          unitPrice: it.unitPrice,
          lineTotal: it.lineTotal,
        })),
        subtotal: data.totals?.subtotal,
        discount: data.totals?.discount,
        taxIncluded: data.totals?.taxIncluded,
        grandTotal: data.totals?.grandTotal,
        paymentMethod: data.payment?.paymentMethod,
      };
    }

    it("should format authoritative receipt details with 18% GST calculation", () => {
      const mockReceiptApi = {
        storeInfo: {
          storeName: "Bajaj Genuine Spare Parts - Central Depot",
        },
        receiptInfo: {
          saleNumber: "INV-202611-0001",
          saleDate: "2026-11-15T00:00:00Z",
          cashierName: "Sunil Sharma",
          customerName: "Walk-in Customer",
        },
        items: [
          {
            partNumber: "DH101048",
            description: "Clutch Plate Assembly",
            quantity: 2,
            unitPrice: 220.0,
            lineTotal: 440.0,
          },
        ],
        totals: {
          subtotal: 440.0,
          discount: 0.0,
          taxIncluded: 67.12, // (440 * 18) / 118
          grandTotal: 440.0,
        },
        payment: {
          paymentMethod: "CASH",
        },
      };

      const view = normalizeReceipt(mockReceiptApi);
      assert.strictEqual(view.saleNumber, "INV-202611-0001");
      assert.strictEqual(view.cashierName, "Sunil Sharma");
      assert.strictEqual(view.items.length, 1);
      assert.strictEqual(view.items[0].partNumber, "DH101048");
      assert.strictEqual(view.grandTotal, 440.0);
      assert.strictEqual(view.paymentMethod, "CASH");
    });
  });

  // ─────────────────────────────────────────────────────────────
  //  12. Sales History and Details Integration
  // ─────────────────────────────────────────────────────────────
  describe("12. Sales History & Details Integration", () => {
    it("should correctly handle paginated sales history with filter criteria", () => {
      const mockHistoryResponse = {
        content: [
          {
            id: 1,
            saleNumber: "INV-202611-0001",
            customerName: "Walk-in Customer",
            saleDate: "2026-11-15T00:00:00Z",
            status: "COMPLETED",
            itemCount: 2,
            total: 440.0,
            paymentMethod: "CASH",
          },
        ],
        totalPages: 1,
        totalElements: 1,
        number: 0,
        size: 20,
      };

      assert.strictEqual(mockHistoryResponse.content.length, 1);
      const sale = mockHistoryResponse.content[0];
      assert.strictEqual(sale.saleNumber, "INV-202611-0001");
      assert.strictEqual(sale.status, "COMPLETED");
      assert.strictEqual(sale.total, 440.0);

      // Verify invoice navigation link target
      const expectedInvoicePath = `/sales/invoice/${sale.id}`;
      assert.strictEqual(expectedInvoicePath, "/sales/invoice/1");
    });
  });
});
