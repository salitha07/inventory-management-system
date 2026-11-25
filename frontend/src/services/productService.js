
import api from "./api";

const PRODUCT_URL = "/api/v1/products";

const productService = {
  // Get products with search, filters, pagination and sorting
  getAllProducts: async (params = {}) => {
    const response = await api.get(PRODUCT_URL, { params });
    return response.data;
  },

  // Get product by ID
  getProductById: async (id) => {
    const response = await api.get(`${PRODUCT_URL}/${id}`);
    return response.data;
  },

  // Create a new product
  createProduct: async (productData) => {
    const response = await api.post(PRODUCT_URL, productData);
    return response.data;
  },

  // Update an existing product
  updateProduct: async (id, productData) => {
    const response = await api.put(
      `${PRODUCT_URL}/${id}`,
      productData
    );
    return response.data;
  },

  // Activate or deactivate a product
  updateProductStatus: async (id, active) => {
    const response = await api.patch(
      `${PRODUCT_URL}/${id}/status`,
      { active }
    );
    return response.data;
  },
};

export default productService;
