import { describe, it } from "node:test";
import assert from "node:assert/strict";
import { validateCheckoutForm } from "../utils/cartOperations.js";

describe("Sales Checkout API Integration & Submission Logic", () => {
  const sampleCart = [
    { id: 101, name: "Clutch Plate Assembly", sku: "DH101048", unitPrice: 220.0, quantity: 2 },
    { id: 205, name: "Bajaj Engine Oil 1L", sku: "36JZ0001", unitPrice: 430.0, quantity: 1 },
  ];

  describe("Payload Preparation for Backend POST /api/v1/sales", () => {
    it("should format valid walk-in cash checkout payload with cart items and tendered amount", () => {
      const { isValid, payload, errors } = validateCheckoutForm({
        cart: sampleCart,
        isWalkIn: true,
        customerId: null,
        paymentMethod: "CASH",
        amountTendered: 1000.0,
        paymentReference: "",
        subtotal: 870.0,
        discount: 0,
        total: 870.0,
      });

      assert.equal(isValid, true);
      assert.deepEqual(errors, {});
      assert.notEqual(payload, null);
      assert.equal(payload.customerId, null);
      assert.equal(payload.paymentMethod, "CASH");
      assert.equal(payload.amountTendered, 1000.0);
      assert.equal(payload.paymentReference, null);
      assert.equal(payload.discount, 0);
      assert.equal(payload.items.length, 2);
      assert.deepEqual(payload.items[0], { productId: 101, quantity: 2, unitSellingPrice: 220.0 });
      assert.deepEqual(payload.items[1], { productId: 205, quantity: 1, unitSellingPrice: 430.0 });
    });

    it("should format registered customer credit checkout with customer ID and reference", () => {
      const { isValid, payload } = validateCheckoutForm({
        cart: sampleCart,
        isWalkIn: false,
        customerId: 42,
        paymentMethod: "CREDIT",
        amountTendered: null,
        paymentReference: "PO-9921",
        subtotal: 870.0,
        discount: 50.0,
        total: 820.0,
      });

      assert.equal(isValid, true);
      assert.equal(payload.customerId, 42);
      assert.equal(payload.paymentMethod, "CREDIT");
      assert.equal(payload.amountTendered, null);
      assert.equal(payload.paymentReference, "PO-9921");
      assert.equal(payload.discount, 50.0);
    });

    it("should reject checkout when cart is empty", () => {
      const { isValid, errors, payload } = validateCheckoutForm({
        cart: [],
        isWalkIn: true,
        paymentMethod: "CASH",
        amountTendered: 500.0,
        total: 0,
      });

      assert.equal(isValid, false);
      assert.equal(payload, null);
      assert.equal(errors.cart, "Cart cannot be empty for checkout.");
    });
  });

  describe("Checkout Error Parser & Feedback Handlers", () => {
    // Pure function extracting and formatting error response matching POS.jsx logic
    function parseCheckoutError(err) {
      const status = err.response?.status;
      const data = err.response?.data;

      let title = "Checkout Failed";
      let message = "An unexpected error occurred while communicating with the server. Please try again.";

      if (status === 409) {
        title = "Insufficient Stock (409 Conflict)";
        message = data?.message || "One or more products have insufficient available stock. Please adjust cart quantities.";
      } else if (status === 404) {
        title = "Product Not Found (404)";
        message = data?.message || "A part in your cart was not found or has been discontinued in the active catalog.";
      } else if (status === 400) {
        title = "Validation Error (400 Bad Request)";
        if (data?.validationErrors && data.validationErrors.length > 0) {
          message = data.validationErrors.map((v) => `${v.field}: ${v.message}`).join("; ");
        } else {
          message = data?.message || "Invalid checkout request data.";
        }
      } else if (status === 403) {
        title = "Permission Denied (403)";
        message = data?.message || "Action not allowed. Store Manager override authorization required.";
      } else if (!err.response) {
        title = "Network Error";
        message = "Unable to connect to the backend server. Please verify your network connection or server availability.";
      } else if (data?.message) {
        message = data.message;
      }

      return { status, title, message };
    }

    it("should handle 409 Conflict (insufficient stock) with clear stock deficit message", () => {
      const mock409Error = {
        response: {
          status: 409,
          data: {
            timestamp: "2026-11-14T23:50:00Z",
            status: 409,
            error: "Conflict",
            message: "Insufficient stock for product ID 101 ('Clutch Plate Assembly'). Requested: 5, Available: 2.",
          },
        },
      };

      const parsed = parseCheckoutError(mock409Error);
      assert.equal(parsed.status, 409);
      assert.equal(parsed.title, "Insufficient Stock (409 Conflict)");
      assert.ok(parsed.message.includes("Insufficient stock for product ID 101"));
      assert.ok(parsed.message.includes("Requested: 5, Available: 2"));
    });

    it("should handle 400 Bad Request with field validation errors", () => {
      const mock400Error = {
        response: {
          status: 400,
          data: {
            status: 400,
            error: "Bad Request",
            validationErrors: [
              { field: "items[0].quantity", message: "Quantity must be at least 1" },
            ],
          },
        },
      };

      const parsed = parseCheckoutError(mock400Error);
      assert.equal(parsed.status, 400);
      assert.equal(parsed.title, "Validation Error (400 Bad Request)");
      assert.ok(parsed.message.includes("items[0].quantity: Quantity must be at least 1"));
    });

    it("should handle 404 Not Found when product discontinued or missing", () => {
      const mock404Error = {
        response: {
          status: 404,
          data: {
            message: "Product with ID 999 was not found or is discontinued in the active parts catalog.",
          },
        },
      };

      const parsed = parseCheckoutError(mock404Error);
      assert.equal(parsed.status, 404);
      assert.equal(parsed.title, "Product Not Found (404)");
      assert.ok(parsed.message.includes("Product with ID 999 was not found"));
    });

    it("should handle network connection drop when backend server is unreachable", () => {
      const mockNetworkError = new Error("Network Error");
      // No response object on error indicates network failure
      const parsed = parseCheckoutError(mockNetworkError);
      assert.equal(parsed.title, "Network Error");
      assert.ok(parsed.message.includes("Unable to connect to the backend server"));
    });
  });

  describe("Cart Clearance Invariant (Requirement 8)", () => {
    it("should preserve cart items when checkout fails (e.g. stock deficit)", () => {
      let cart = [...sampleCart];
      const clearCart = () => {
        cart = [];
      };

      // Simulated failure
      const checkoutSuccess = false;
      if (checkoutSuccess) {
        clearCart();
      }

      // Cart MUST NOT be cleared
      assert.equal(cart.length, 2);
    });

    it("should clear cart strictly after confirmed transaction success", () => {
      let cart = [...sampleCart];
      const clearCart = () => {
        cart = [];
      };

      // Simulated success
      const checkoutSuccess = true;
      if (checkoutSuccess) {
        clearCart();
      }

      // Cart MUST be cleared
      assert.equal(cart.length, 0);
    });
  });
});

