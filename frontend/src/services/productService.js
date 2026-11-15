import api from "./api";

/**
 * Search products by name, part number, or barcode.
 *
 * Consumes: GET /api/v1/products/search?q={query}&limit={limit}
 * Owned by: Member 2 (Products module)
 *
 * @param {string} query - Search term (part number, name, or barcode)
 * @param {number} [limit=20] - Maximum results to return
 * @returns {Promise<Array>} Array of matching product objects
 */
export const searchProducts = async (query, limit = 20) => {
  const response = await api.get("/products/search", {
    params: { q: query, limit },
  });
  return response.data;
};

