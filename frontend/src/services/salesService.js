import api from "./api";

/**
 * Service for Point of Sale (POS) and Sales checkout operations.
 *
 * Implements the approved POS API contract at /api/v1/sales.
 */

/**
 * Submits a sales checkout request to create and finalize an invoice.
 *
 * @param {Object} checkoutData - SaleCreateRequest payload matching backend contract
 * @param {number|null} [checkoutData.customerId] - Registered customer ID or null for walk-in
 * @param {string} checkoutData.paymentMethod - CASH, CARD, QR_PAYMENT, BANK_TRANSFER, CREDIT
 * @param {number|null} [checkoutData.amountTendered] - Tendered cash amount
 * @param {string|null} [checkoutData.paymentReference] - Card slip, LankaQR ref, or bank ref
 * @param {number} checkoutData.discount - Header discount amount
 * @param {Array<{productId: number, quantity: number, unitSellingPrice?: number}>} checkoutData.items - Cart items
 * @returns {Promise<Object>} SaleResponse details (id, saleNumber, total, changeDue, etc.)
 */
export const createSale = async (checkoutData) => {
  const response = await api.post("/sales", checkoutData);
  return response.data;
};

/**
 * Retrieves details of a specific sale invoice.
 *
 * @param {number|string} saleId - Sale primary key
 * @returns {Promise<Object>} SaleResponse details
 */
export const getSaleById = async (saleId) => {
  const response = await api.get(`/sales/${saleId}`);
  return response.data;
};

/**
 * Retrieves formatted receipt data optimized for printing and receipt view.
 *
 * Consumes: GET /api/v1/sales/{id}/receipt
 *
 * @param {number|string} saleId - Sale primary key
 * @returns {Promise<Object>} ReceiptResponse details
 */
export const getReceipt = async (saleId) => {
  const response = await api.get(`/sales/${saleId}/receipt`);
  return response.data;
};

/**
 * Retrieves a paginated list of sales.
 *
 * @param {Object} [params] - Pagination & filter parameters (page, size, sort)
 * @returns {Promise<Object>} Paginated sales response
 */
export const getSalesHistory = async (params = {}) => {
  const response = await api.get("/sales", { params });
  return response.data;
};

export default {
  createSale,
  getSaleById,
  getReceipt,
  getSalesHistory,
};

