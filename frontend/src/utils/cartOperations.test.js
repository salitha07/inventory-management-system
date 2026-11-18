import { describe, it } from "node:test";
import assert from "node:assert/strict";
import {
  addItemToCart,
  removeItemFromCart,
  updateItemQuantity,
  increaseItemQuantity,
  decreaseItemQuantity,
  isValidQuantity,
  isValidDiscount,
  calculateLineTotal,
  calculateSubtotal,
  calculateFinalTotal,
  calculateChangeDue,
  validateCheckoutForm,
  clearCartItems,
  calculateCartTotals,
  PAYMENT_METHODS,
  formatPaymentMethod,
} from "./cartOperations.js";

describe("Cart Operations & Checkout Validation", () => {
  const productA = {
    productId: 101,
    name: "Clutch Plate Assembly",
    partNumber: "DH101048",
    sellingPrice: 220.0,
    availableQuantity: 10,
  };

  const productB = {
    productId: 102,
    name: "Brake Shoe Set",
    partNumber: "DJ151034",
    sellingPrice: 150.0,
    availableQuantity: 5,
  };

  describe("calculateChangeDue", () => {
    it("should calculate exact change due for cash payments", () => {
      assert.equal(calculateChangeDue(500.0, 440.0), 60.0);
      assert.equal(calculateChangeDue(1000.0, 870.0), 130.0);
    });

    it("should return 0 when exact amount is tendered", () => {
      assert.equal(calculateChangeDue(440.0, 440.0), 0);
    });

    it("should return 0 when tendered amount is less than total", () => {
      assert.equal(calculateChangeDue(300.0, 440.0), 0);
    });
  });

  describe("validateCheckoutForm", () => {
    const sampleCart = [
      { id: 101, unitPrice: 220.0, quantity: 2 }, // 440.00
    ];

    it("should validate a valid walk-in cash transaction according to API contract", () => {
      const result = validateCheckoutForm({
        cart: sampleCart,
        isWalkIn: true,
        customerId: null,
        paymentMethod: "CASH",
        amountTendered: 500.0,
        paymentReference: null,
        total: 440.0,
        subtotal: 440.0,
        discount: 0,
      });

      assert.equal(result.isValid, true);
      assert.deepEqual(result.errors, {});
      assert.equal(result.payload.customerId, null, "Walk-in customer must have null customerId");
      assert.equal(result.payload.paymentMethod, "CASH");
      assert.equal(result.payload.amountTendered, 500.0);
      assert.equal(result.payload.discount, 0);
      assert.equal(result.payload.items.length, 1);
      assert.equal(result.payload.items[0].productId, 101);
      assert.equal(result.payload.items[0].quantity, 2);
      assert.equal(result.payload.items[0].unitSellingPrice, 220.0);
    });

    it("should validate a valid registered customer account sale on credit terms", () => {
      const result = validateCheckoutForm({
        cart: sampleCart,
        isWalkIn: false,
        customerId: 42,
        paymentMethod: "CREDIT",
        paymentReference: "PO-GARAGE-882",
        total: 440.0,
        subtotal: 440.0,
        discount: 0,
      });

      assert.equal(result.isValid, true);
      assert.equal(result.payload.customerId, 42);
      assert.equal(result.payload.paymentMethod, "CREDIT");
      assert.equal(result.payload.amountTendered, null);
      assert.equal(result.payload.paymentReference, "PO-GARAGE-882");
    });

    it("should reject walk-in sales requesting store credit", () => {
      const result = validateCheckoutForm({
        cart: sampleCart,
        isWalkIn: true,
        customerId: null,
        paymentMethod: "CREDIT",
        total: 440.0,
        subtotal: 440.0,
      });

      assert.equal(result.isValid, false);
      assert.ok(result.errors.customerId, "Should require registered account for credit");
    });

    it("should reject cash sale when amount tendered is less than total", () => {
      const result = validateCheckoutForm({
        cart: sampleCart,
        isWalkIn: true,
        paymentMethod: "CASH",
        amountTendered: 400.0, // Total is 440.00
        total: 440.0,
        subtotal: 440.0,
      });

      assert.equal(result.isValid, false);
      assert.ok(result.errors.amountTendered);
    });

    it("should reject checkout when cart is empty", () => {
      const result = validateCheckoutForm({
        cart: [],
        paymentMethod: "CASH",
        amountTendered: 100,
        total: 0,
      });

      assert.equal(result.isValid, false);
      assert.ok(result.errors.cart);
    });

    it("should accept QR_PAYMENT method", () => {
      const result = validateCheckoutForm({
        cart: sampleCart,
        paymentMethod: "QR_PAYMENT",
        total: 440.0,
      });

      assert.equal(result.isValid, true);
    });

    it("should reject UPI payment method for new sales/checkout", () => {
      const result = validateCheckoutForm({
        cart: sampleCart,
        paymentMethod: "UPI",
        total: 440.0,
      });

      assert.equal(result.isValid, false);
      assert.ok(result.errors.paymentMethod);
    });

    it("should reject invalid payment methods", () => {
      const result = validateCheckoutForm({
        cart: sampleCart,
        paymentMethod: "BITCOIN",
        total: 440.0,
      });

      assert.equal(result.isValid, false);
      assert.ok(result.errors.paymentMethod);
    });
  });

  describe("PAYMENT_METHODS", () => {
    it("should contain all 5 approved payment methods including QR_PAYMENT", () => {
      const values = PAYMENT_METHODS.map((m) => m.value);
      assert.deepEqual(values, ["CASH", "CARD", "QR_PAYMENT", "BANK_TRANSFER", "CREDIT"]);
    });

    it("should display 'QR Payment (LankaQR)' as the label for QR_PAYMENT", () => {
      const qrOption = PAYMENT_METHODS.find((m) => m.value === "QR_PAYMENT");
      assert.ok(qrOption);
      assert.equal(qrOption.label, "QR Payment (LankaQR)");
    });
  });

  describe("formatPaymentMethod", () => {
    it("should format QR_PAYMENT as QR Payment (LankaQR)", () => {
      assert.equal(formatPaymentMethod("QR_PAYMENT"), "QR Payment (LankaQR)");
    });

    it("should format legacy UPI as UPI", () => {
      assert.equal(formatPaymentMethod("UPI"), "UPI");
    });

    it("should format standard methods properly", () => {
      assert.equal(formatPaymentMethod("CASH"), "Cash");
      assert.equal(formatPaymentMethod("CARD"), "Debit / Credit Card");
      assert.equal(formatPaymentMethod("BANK_TRANSFER"), "Bank Transfer");
      assert.equal(formatPaymentMethod("CREDIT"), "Customer Store Credit");
    });
  });

  describe("calculateLineTotal", () => {
    it("should calculate line total as unit price multiplied by quantity", () => {
      assert.equal(calculateLineTotal(220.0, 2), 440.0);
      assert.equal(calculateLineTotal(150.0, 3), 450.0);
    });

    it("should avoid floating-point multiplication errors", () => {
      assert.equal(calculateLineTotal(19.99, 3), 59.97);
      assert.equal(calculateLineTotal(70.1, 3), 210.3);
    });
  });

  describe("calculateSubtotal & calculateFinalTotal", () => {
    it("should return 0 for an empty cart", () => {
      assert.equal(calculateSubtotal([]), 0);
    });

    it("should calculate subtotal as the sum of line totals", () => {
      const cart = [
        { id: 101, unitPrice: 220.0, quantity: 2 },
        { id: 102, unitPrice: 150.0, quantity: 1 },
      ];
      assert.equal(calculateSubtotal(cart), 590.0);
      assert.equal(calculateFinalTotal(590.0, 40.0), 550.0);
    });

    it("should prevent negative payable totals", () => {
      assert.equal(calculateFinalTotal(100.0, 150.0), 0.0);
    });
  });

  describe("isValidDiscount", () => {
    it("should accept valid non-negative discounts within subtotal", () => {
      assert.equal(isValidDiscount(0, 100), true);
      assert.equal(isValidDiscount("25.50", 100), true);
      assert.equal(isValidDiscount(100, 100), true);
    });

    it("should reject negative discount and discounts exceeding subtotal", () => {
      assert.equal(isValidDiscount(-5, 100), false);
      assert.equal(isValidDiscount(150, 100), false);
    });
  });

  describe("Dynamic Cart Operations", () => {
    it("should add, update, and remove items consistently", () => {
      let cart = addItemToCart([], productA);
      assert.equal(cart.length, 1);

      cart = increaseItemQuantity(cart, 101);
      assert.equal(cart[0].quantity, 2);

      cart = decreaseItemQuantity(cart, 101);
      assert.equal(cart[0].quantity, 1);

      cart = removeItemFromCart(cart, 101);
      assert.equal(cart.length, 0);
      assert.deepEqual(clearCartItems(), []);
    });
  });
});
