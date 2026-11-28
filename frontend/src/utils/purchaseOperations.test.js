import test from "node:test";
import assert from "node:assert/strict";

import {
  MAX_PURCHASE_AMOUNT,
  MAX_QUANTITY,
  PURCHASE_STATUS,
  isValidQuantity,
  isValidAmount,
  calculateLineTotal,
  calculateSubtotal,
  calculatePurchaseTotal,
  validatePurchaseForm,
  canAccessPurchases,
  canCreatePurchase,
  canReceivePurchase,
  canCancelPurchase,
} from "./purchaseOperations.js";

function createValidForm() {
  return {
    supplierId: "1",
    supplierInvoiceNumber: "INV-001",
    purchaseDate: "2026-11-26",
    discount: "100",
    items: [
      {
        productId: "1",
        quantity: "5",
        unitCost: "250",
      },
      {
        productId: "2",
        quantity: "3",
        unitCost: "500",
      },
    ],
  };
}

test("accepts valid purchase quantities", () => {
  assert.equal(isValidQuantity(1), true);
  assert.equal(isValidQuantity("10"), true);
  assert.equal(isValidQuantity(MAX_QUANTITY), true);
});

test("rejects invalid purchase quantities", () => {
  for (const value of [
    0, -1, 1.5, "", null, "abc", MAX_QUANTITY + 1,
  ]) {
    assert.equal(isValidQuantity(value), false);
  }
});

test("accepts valid monetary amounts", () => {
  assert.equal(isValidAmount(0), true);
  assert.equal(isValidAmount("250.50"), true);
  assert.equal(isValidAmount(MAX_PURCHASE_AMOUNT), true);
});

test("rejects invalid monetary amounts", () => {
  for (const value of [
    -1, "", null, "abc", "10.123",
    MAX_PURCHASE_AMOUNT + 1,
  ]) {
    assert.equal(isValidAmount(value), false);
  }
});

test("calculates purchase item line total", () => {
  assert.equal(calculateLineTotal(5, 250), 1250);
});

test("calculates decimal line total accurately", () => {
  assert.equal(calculateLineTotal(3, 0.1), 0.3);
});

test("returns zero for invalid line inputs", () => {
  assert.equal(calculateLineTotal(0, 250), 0);
  assert.equal(calculateLineTotal(5, -100), 0);
});

test("calculates purchase subtotal", () => {
  const form = createValidForm();
  assert.equal(calculateSubtotal(form.items), 2750);
});

test("calculates purchase total after discount", () => {
  const form = createValidForm();

  assert.deepEqual(
    calculatePurchaseTotal(form.items, 100),
    {
      subtotal: 2750,
      discount: 100,
      total: 2650,
    }
  );
});

test("calculates empty purchase subtotal", () => {
  assert.equal(calculateSubtotal([]), 0);
});

test("validates a complete purchase form", () => {
  const result = validatePurchaseForm(createValidForm());

  assert.equal(result.isValid, true);
  assert.deepEqual(result.errors, {});
});

test("generates the correct purchase API payload", () => {
  const result = validatePurchaseForm(createValidForm());

  assert.deepEqual(result.payload, {
    supplierId: 1,
    supplierInvoiceNumber: "INV-001",
    purchaseDate: "2026-11-26",
    discount: 100,
    items: [
      {
        productId: 1,
        quantity: 5,
        unitCost: 250,
      },
      {
        productId: 2,
        quantity: 3,
        unitCost: 500,
      },
    ],
  });
});

test("rejects missing supplier", () => {
  const form = createValidForm();
  form.supplierId = "";

  const result = validatePurchaseForm(form);

  assert.equal(result.isValid, false);
  assert.ok(result.errors.supplierId);
  assert.equal(result.payload, null);
});

test("rejects missing supplier invoice number", () => {
  const form = createValidForm();
  form.supplierInvoiceNumber = "";

  assert.ok(
    validatePurchaseForm(form).errors.supplierInvoiceNumber
  );
});

test("rejects oversized invoice number", () => {
  const form = createValidForm();
  form.supplierInvoiceNumber = "A".repeat(101);

  assert.ok(
    validatePurchaseForm(form).errors.supplierInvoiceNumber
  );
});

test("rejects invalid purchase date", () => {
  const form = createValidForm();
  form.purchaseDate = "2026-02-30";

  assert.ok(validatePurchaseForm(form).errors.purchaseDate);
});

test("rejects purchases without items", () => {
  const form = createValidForm();
  form.items = [];

  assert.ok(validatePurchaseForm(form).errors.items);
});

test("rejects invalid product ID", () => {
  const form = createValidForm();
  form.items[0].productId = "";

  assert.ok(
    validatePurchaseForm(form).errors.itemErrors[0].productId
  );
});

test("rejects invalid item quantity", () => {
  const form = createValidForm();
  form.items[0].quantity = 0;

  assert.ok(
    validatePurchaseForm(form).errors.itemErrors[0].quantity
  );
});

test("rejects invalid item unit cost", () => {
  const form = createValidForm();
  form.items[0].unitCost = -100;

  assert.ok(
    validatePurchaseForm(form).errors.itemErrors[0].unitCost
  );
});

test("rejects discount exceeding subtotal", () => {
  const form = createValidForm();
  form.discount = 3000;

  assert.ok(validatePurchaseForm(form).errors.discount);
});

test("rejects purchase line total exceeding maximum", () => {
  const form = createValidForm();
  form.items[0].quantity = 2;
  form.items[0].unitCost = MAX_PURCHASE_AMOUNT;

  assert.ok(
    validatePurchaseForm(form).errors.itemErrors[0].lineTotal
  );
});

test("rejects purchase subtotal exceeding maximum", () => {
  const form = createValidForm();

  form.items = [
    { productId: 1, quantity: 1, unitCost: MAX_PURCHASE_AMOUNT },
    { productId: 2, quantity: 1, unitCost: 1 },
  ];

  assert.ok(validatePurchaseForm(form).errors.subtotal);
});

test("allows authorized roles to access purchases", () => {
  for (const role of ["ADMIN", "MANAGER", "INVENTORY_STAFF"]) {
    assert.equal(canAccessPurchases(role), true);
  }
});

test("denies unauthorized purchase access", () => {
  assert.equal(canAccessPurchases("CASHIER"), false);
  assert.equal(canAccessPurchases(null), false);
});

test("validates purchase creation permissions", () => {
  assert.equal(canCreatePurchase("ADMIN"), true);
  assert.equal(canCreatePurchase("MANAGER"), true);
  assert.equal(canCreatePurchase("INVENTORY_STAFF"), true);
  assert.equal(canCreatePurchase("CASHIER"), false);
});

test("validates purchase receiving permissions", () => {
  assert.equal(
    canReceivePurchase("ADMIN", PURCHASE_STATUS.DRAFT),
    true
  );

  assert.equal(
    canReceivePurchase("MANAGER", PURCHASE_STATUS.DRAFT),
    true
  );

  assert.equal(
    canReceivePurchase("INVENTORY_STAFF", PURCHASE_STATUS.DRAFT),
    false
  );

  assert.equal(
    canReceivePurchase("ADMIN", PURCHASE_STATUS.RECEIVED),
    false
  );

  assert.equal(
    canReceivePurchase("ADMIN", PURCHASE_STATUS.CANCELLED),
    false
  );
});

test("validates purchase cancellation permissions", () => {
  assert.equal(
    canCancelPurchase("ADMIN", PURCHASE_STATUS.DRAFT),
    true
  );

  assert.equal(
    canCancelPurchase("MANAGER", PURCHASE_STATUS.DRAFT),
    true
  );

  assert.equal(
    canCancelPurchase("CASHIER", PURCHASE_STATUS.DRAFT),
    false
  );

  assert.equal(
    canCancelPurchase("INVENTORY_STAFF", PURCHASE_STATUS.DRAFT),
    false
  );

  assert.equal(
    canCancelPurchase("ADMIN", PURCHASE_STATUS.RECEIVED),
    false
  );

  assert.equal(
    canCancelPurchase("ADMIN", PURCHASE_STATUS.CANCELLED),
    false
  );
});