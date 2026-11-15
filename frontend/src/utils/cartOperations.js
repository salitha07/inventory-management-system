/**
 * Pure cart operation utilities to ensure consistent, immutable cart state management
 * and precise financial calculations without floating-point errors.
 */

import { formatPrice } from "./currency.js";

export const PAYMENT_METHODS = [
  { value: "CASH", label: "Cash" },
  { value: "CARD", label: "Debit / Credit Card" },
  { value: "QR_PAYMENT", label: "QR Payment (LankaQR)" },
  { value: "BANK_TRANSFER", label: "Bank Transfer" },
  { value: "CREDIT", label: "Customer Store Credit" },
];

/**
 * Formats a payment method enum value into a user-facing label.
 * Gracefully preserves and formats legacy historical payment methods like UPI.
 *
 * @param {string} [method] - Payment method code
 * @returns {string} Formatted label
 */
export const formatPaymentMethod = (method) => {
  if (!method) return "Cash";
  const norm = String(method).toUpperCase();
  const found = PAYMENT_METHODS.find((m) => m.value === norm);
  if (found) return found.label;
  if (norm === "UPI") return "UPI";
  return method;
};

/**
 * Validates whether a quantity is a valid positive integer and within stock limits.
 *
 * @param {*} value - The quantity value to validate
 * @param {number} [availableQuantity] - Optional stock limit
 * @returns {boolean} True if valid positive integer and within bounds
 */
export function isValidQuantity(value, availableQuantity) {
  if (value === null || value === undefined || value === "") {
    return false;
  }

  const str = String(value).trim();
  if (!/^\d+$/.test(str)) {
    return false;
  }

  const num = Number(str);
  if (num <= 0 || !Number.isSafeInteger(num)) {
    return false;
  }

  if (
    availableQuantity !== undefined &&
    availableQuantity !== null &&
    num > availableQuantity
  ) {
    return false;
  }

  return true;
}

/**
 * Validates whether a discount value conforms to the approved financial rules:
 * - Must be non-negative (>= 0)
 * - Must have at most 2 decimal places
 * - Must not exceed the subtotal ($0.00 <= discount <= subtotal)
 *
 * @param {*} discount - Discount value to validate
 * @param {number} [subtotal=0] - Cart subtotal
 * @returns {boolean} True if valid discount value
 */
export function isValidDiscount(discount, subtotal = 0) {
  if (discount === null || discount === undefined || discount === "") {
    return false;
  }

  const str = String(discount).trim();
  if (!/^\d+(\.\d{1,2})?$/.test(str)) {
    return false;
  }

  const discountNum = Number(str);
  if (isNaN(discountNum) || discountNum < 0) {
    return false;
  }

  const subtotalNum = Math.max(0, Number(subtotal) || 0);
  const discountInPaise = Math.round(discountNum * 100);
  const subtotalInPaise = Math.round(subtotalNum * 100);

  return discountInPaise <= subtotalInPaise;
}

/**
 * Calculates the line total for an item as unit price multiplied by quantity.
 * Converts to integer paise before multiplication to prevent floating-point errors.
 *
 * @param {number|string} unitPrice - Item unit selling price
 * @param {number|string} quantity - Quantity of items
 * @returns {number} Line total rounded to 2 decimal places
 */
export function calculateLineTotal(unitPrice, quantity) {
  const priceInPaise = Math.round((Number(unitPrice) || 0) * 100);
  const qty = Math.max(0, parseInt(quantity, 10) || 0);
  const lineTotalInPaise = priceInPaise * qty;
  return lineTotalInPaise / 100;
}

/**
 * Calculates the subtotal as the sum of line totals across all cart items.
 * Handles an empty cart returning 0.
 * Uses integer paise accumulation to completely eliminate floating-point drift.
 *
 * @param {Array} cart - Current cart items
 * @returns {number} Subtotal rounded to 2 decimal places
 */
export function calculateSubtotal(cart = []) {
  if (!Array.isArray(cart) || cart.length === 0) {
    return 0;
  }

  const subtotalInPaise = cart.reduce((sumInPaise, item) => {
    const priceInPaise = Math.round((Number(item.unitPrice) || 0) * 100);
    const qty = Math.max(0, parseInt(item.quantity, 10) || 0);
    return sumInPaise + priceInPaise * qty;
  }, 0);

  return subtotalInPaise / 100;
}

/**
 * Calculates the final payable total as subtotal minus discount.
 * Uses integer paise arithmetic and guarantees the total is never negative.
 *
 * @param {number|string} subtotal - Cart subtotal
 * @param {number|string} [discount=0] - Applied discount
 * @returns {number} Final total payable (minimum 0.00)
 */
export function calculateFinalTotal(subtotal, discount = 0) {
  const subtotalInPaise = Math.max(0, Math.round((Number(subtotal) || 0) * 100));
  const rawDiscountInPaise = Math.max(0, Math.round((Number(discount) || 0) * 100));
  const effectiveDiscountInPaise = Math.min(rawDiscountInPaise, subtotalInPaise);
  const totalInPaise = subtotalInPaise - effectiveDiscountInPaise;
  return totalInPaise / 100;
}

/**
 * Calculates change due for cash transactions.
 * Uses integer paise to avoid float errors.
 *
 * @param {number|string} amountTendered - Cash tendered by customer
 * @param {number|string} total - Total payable
 * @returns {number} Change due (minimum 0.00)
 */
export function calculateChangeDue(amountTendered, total) {
  const tenderedInPaise = Math.max(0, Math.round((Number(amountTendered) || 0) * 100));
  const totalInPaise = Math.max(0, Math.round((Number(total) || 0) * 100));
  if (tenderedInPaise < totalInPaise) {
    return 0;
  }
  return (tenderedInPaise - totalInPaise) / 100;
}

/**
 * Computes subtotal, total items count, effective discount, and grand total.
 * Handles empty cart gracefully and guarantees non-negative payable totals.
 *
 * @param {Array} cart - Current cart items
 * @param {number|string} [discount=0] - Requested discount amount
 * @returns {Object} Cart totals with financial breakdown
 */
export function calculateCartTotals(cart = [], discount = 0) {
  const subtotal = calculateSubtotal(cart);
  const totalItemsCount = Array.isArray(cart)
    ? cart.reduce(
        (sum, item) => sum + Math.max(0, parseInt(item.quantity, 10) || 0),
        0
      )
    : 0;

  const subtotalInPaise = Math.round(subtotal * 100);
  const rawDiscountInPaise = Math.max(0, Math.round((Number(discount) || 0) * 100));
  const isDiscountExceeded = rawDiscountInPaise > subtotalInPaise;

  const effectiveDiscountInPaise = Math.min(rawDiscountInPaise, subtotalInPaise);
  const totalInPaise = Math.max(0, subtotalInPaise - effectiveDiscountInPaise);

  return {
    subtotal: subtotalInPaise / 100,
    totalItemsCount,
    discount: effectiveDiscountInPaise / 100,
    rawDiscount: rawDiscountInPaise / 100,
    totalAmount: totalInPaise / 100,
    isDiscountExceeded,
  };
}

/**
 * Validates checkout form data against the approved POS API contract.
 *
 * @param {Object} formData
 * @param {Array} formData.cart - Cart items
 * @param {boolean} formData.isWalkIn - Whether walk-in customer
 * @param {number|string|null} [formData.customerId] - Customer ID if registered
 * @param {string} formData.paymentMethod - CASH, CARD, QR_PAYMENT, BANK_TRANSFER, CREDIT
 * @param {number|string} [formData.amountTendered] - Tendered amount for cash sales
 * @param {string} [formData.paymentReference] - Optional reference code
 * @param {number} formData.total - Net payable total
 * @param {number} formData.subtotal - Cart subtotal
 * @param {number} [formData.discount=0] - Header discount
 * @returns {Object} Validation result { isValid, errors, payload }
 */
export function validateCheckoutForm({
  cart = [],
  isWalkIn = true,
  customerId = null,
  paymentMethod = "CASH",
  amountTendered = 0,
  paymentReference = "",
  total = 0,
  subtotal = 0,
  discount = 0,
} = {}) {
  const errors = {};

  if (!Array.isArray(cart) || cart.length === 0) {
    errors.cart = "Cart cannot be empty for checkout.";
  }

  const validPaymentMethods = ["CASH", "CARD", "QR_PAYMENT", "BANK_TRANSFER", "CREDIT"];
  if (!validPaymentMethods.includes(paymentMethod)) {
    errors.paymentMethod = "Please select a valid payment method.";
  }

  // Walk-in sales cannot use store credit
  if (paymentMethod === "CREDIT" && (isWalkIn || !customerId)) {
    errors.customerId = "Customer store credit requires a registered customer account.";
  }

  // Cash validation
  if (paymentMethod === "CASH") {
    const strTendered = String(amountTendered ?? "").trim();
    const tenderedNum = Number(strTendered);
    if (strTendered === "" || isNaN(tenderedNum)) {
      errors.amountTendered = "Amount tendered is required for cash payment.";
    } else if (tenderedNum < total) {
      errors.amountTendered = `Amount tendered must be at least ${formatPrice(total)}.`;
    }
  }

  // Discount validation
  if (Number(discount) > Number(subtotal)) {
    errors.discount = "Discount cannot exceed subtotal.";
  }

  const isValid = Object.keys(errors).length === 0;

  // Prepare approved API payload structure matching POST /api/v1/sales
  const payload = isValid
    ? {
        customerId: isWalkIn ? null : Number(customerId),
        paymentMethod,
        amountTendered: paymentMethod === "CASH" ? Number(amountTendered) : null,
        paymentReference: paymentReference?.trim() || null,
        discount: Number(discount) || 0,
        items: cart.map((item) => ({
          productId: item.id,
          quantity: item.quantity,
          unitSellingPrice: item.unitPrice,
        })),
      }
    : null;

  return { isValid, errors, payload };
}

/**
 * Adds a product to the cart or increments its quantity if already present.
 * Prevents duplicate items in the cart.
 *
 * @param {Array} cart - Current cart items
 * @param {Object} product - Product to add
 * @param {number} [quantity=1] - Quantity to add
 * @returns {Array} Updated cart array
 */
export function addItemToCart(cart = [], product, quantity = 1) {
  if (!product) return cart;

  const productId = product.productId ?? product.id;
  if (productId === undefined || productId === null) return cart;

  const sku = product.sku || product.partNumber || "N/A";
  const name = product.name || "Unnamed Product";
  const unitPrice = Number(
    product.sellingPrice ?? product.unitSellingPrice ?? product.price ?? 0
  );
  const addQty = Math.max(1, parseInt(quantity, 10) || 1);

  const existingItem = cart.find((item) => item.id === productId);

  if (existingItem) {
    return increaseItemQuantity(cart, productId, addQty);
  }

  if (
    product.availableQuantity !== undefined &&
    product.availableQuantity !== null &&
    product.availableQuantity <= 0
  ) {
    return cart;
  }

  const initialQty =
    product.availableQuantity !== undefined && product.availableQuantity !== null
      ? Math.min(addQty, product.availableQuantity)
      : addQty;

  return [
    ...cart,
    {
      id: productId,
      name,
      sku,
      unitPrice,
      quantity: Math.max(1, initialQty),
      availableQuantity: product.availableQuantity,
    },
  ];
}

/**
 * Increases item quantity by step, respecting stock boundaries.
 *
 * @param {Array} cart - Current cart items
 * @param {number|string} productId - Product ID to increase
 * @param {number} [step=1] - Quantity increment
 * @returns {Array} Updated cart array
 */
export function increaseItemQuantity(cart = [], productId, step = 1) {
  if (productId === undefined || productId === null) return cart;
  const inc = Math.max(1, parseInt(step, 10) || 1);

  return cart.map((item) => {
    if (item.id === productId) {
      const nextQty = item.quantity + inc;
      if (item.availableQuantity !== undefined && item.availableQuantity !== null) {
        if (item.quantity >= item.availableQuantity) {
          return item;
        }
        return { ...item, quantity: Math.min(nextQty, item.availableQuantity) };
      }
      return { ...item, quantity: nextQty };
    }
    return item;
  });
}

/**
 * Decreases item quantity by step, preventing zero or negative quantities.
 * Minimum allowed quantity is 1.
 *
 * @param {Array} cart - Current cart items
 * @param {number|string} productId - Product ID to decrease
 * @param {number} [step=1] - Quantity decrement
 * @returns {Array} Updated cart array
 */
export function decreaseItemQuantity(cart = [], productId, step = 1) {
  if (productId === undefined || productId === null) return cart;
  const dec = Math.max(1, parseInt(step, 10) || 1);

  return cart.map((item) => {
    if (item.id === productId) {
      const nextQty = Math.max(1, item.quantity - dec);
      return { ...item, quantity: nextQty };
    }
    return item;
  });
}

/**
 * Updates the quantity of a specific item in the cart.
 * Rejects invalid, non-numeric, zero, negative, or over-stock quantities.
 *
 * @param {Array} cart - Current cart items
 * @param {number|string} productId - Product ID
 * @param {number|string} newQuantity - Desired quantity
 * @returns {Array} Updated cart array (or original cart if input is rejected)
 */
export function updateItemQuantity(cart = [], productId, newQuantity) {
  if (productId === undefined || productId === null) return cart;

  const targetItem = cart.find((item) => item.id === productId);
  if (!targetItem) return cart;

  if (!isValidQuantity(newQuantity, targetItem.availableQuantity)) {
    return cart;
  }

  const qty = parseInt(newQuantity, 10);
  return cart.map((item) =>
    item.id === productId ? { ...item, quantity: qty } : item
  );
}

/**
 * Removes an item from the cart by its ID.
 *
 * @param {Array} cart - Current cart items
 * @param {number|string} productId - Product ID to remove
 * @returns {Array} Updated cart array
 */
export function removeItemFromCart(cart = [], productId) {
  if (productId === undefined || productId === null) return cart;
  return cart.filter((item) => item.id !== productId);
}

/**
 * Clears all items from the cart.
 *
 * @returns {Array} Empty array
 */
export function clearCartItems() {
  return [];
}
