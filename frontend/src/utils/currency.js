/**
 * Centralized utility for formatting currency in Sri Lankan Rupees (Rs.).
 *
 * Requirements:
 * - Displays all monetary values using the prefix "Rs.".
 * - Consistent thousands separators and 2 decimal places.
 *
 * Examples:
 *   formatPrice(0)       -> "Rs. 0.00"
 *   formatPrice(1500)    -> "Rs. 1,500.00"
 *   formatPrice(250.5)   -> "Rs. 250.50"
 */

/**
 * Formats a monetary amount into a standardized Sri Lankan Rupee display string.
 *
 * @param {number|string|null|undefined} amount - Numeric value or parsable string
 * @returns {string} Formatted currency string (e.g. "Rs. 1,500.00")
 */
export function formatPrice(amount) {
  if (amount === null || amount === undefined || amount === "") {
    return "Rs. 0.00";
  }

  const num = typeof amount === "number" ? amount : Number(amount);
  if (isNaN(num)) {
    return "Rs. 0.00";
  }

  const parts = num.toFixed(2).split(".");
  // Insert commas as thousands separators
  parts[0] = parts[0].replace(/\B(?=(\d{3})+(?!\d))/g, ",");
  return `Rs. ${parts.join(".")}`;
}

export const formatCurrency = formatPrice;

export default formatPrice;
