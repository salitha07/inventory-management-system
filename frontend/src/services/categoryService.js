
import api from "./api";

const CATEGORY_URL = "/api/v1/categories";

const categoryService = {
  // Get categories with pagination
  getAllCategories: async (params = {}) => {
    const response = await api.get(CATEGORY_URL, { params });
    return response.data;
  },

  // Get category by ID
  getCategoryById: async (id) => {
    const response = await api.get(`${CATEGORY_URL}/${id}`);
    return response.data;
  },

  // Create a new category
  createCategory: async (categoryData) => {
    const response = await api.post(CATEGORY_URL, categoryData);
    return response.data;
  },

  // Update an existing category
  updateCategory: async (id, categoryData) => {
    const response = await api.put(
      `${CATEGORY_URL}/${id}`,
      categoryData
    );
    return response.data;
  },

  // Activate or deactivate a category
  updateCategoryStatus: async (id, active) => {
    const response = await api.patch(
      `${CATEGORY_URL}/${id}/status`,
      { active }
    );
    return response.data;
  },
};

export default categoryService;
