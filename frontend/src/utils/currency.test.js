import { describe, it } from "node:test";
import assert from "node:assert/strict";
import { formatPrice, formatCurrency } from "./currency.js";

describe("Currency Formatter (Sri Lankan Rupees - Rs.)", () => {
  it("formats zero amount to 'Rs. 0.00'", () => {
    assert.strictEqual(formatPrice(0), "Rs. 0.00");
    assert.strictEqual(formatPrice("0"), "Rs. 0.00");
  });

  it("formats large numbers with thousands separators", () => {
    assert.strictEqual(formatPrice(1500), "Rs. 1,500.00");
    assert.strictEqual(formatPrice(1500.0), "Rs. 1,500.00");
    assert.strictEqual(formatPrice("1500.00"), "Rs. 1,500.00");
    assert.strictEqual(formatPrice(1000000), "Rs. 1,000,000.00");
  });

  it("formats fractional cents/paise accurately to 2 decimal places", () => {
    assert.strictEqual(formatPrice(250.5), "Rs. 250.50");
    assert.strictEqual(formatPrice(250.55), "Rs. 250.55");
    assert.strictEqual(formatPrice(440), "Rs. 440.00");
  });

  it("handles null, undefined, empty, and invalid inputs gracefully", () => {
    assert.strictEqual(formatPrice(null), "Rs. 0.00");
    assert.strictEqual(formatPrice(undefined), "Rs. 0.00");
    assert.strictEqual(formatPrice(""), "Rs. 0.00");
    assert.strictEqual(formatPrice("invalid"), "Rs. 0.00");
    assert.strictEqual(formatPrice(NaN), "Rs. 0.00");
  });

  it("provides formatCurrency as an alias for formatPrice", () => {
    assert.strictEqual(formatCurrency(1500), "Rs. 1,500.00");
  });
});
