import api from "./api";

const PURCHASE_URL = "/purchases";

const purchaseService = {
    // Get all purchases
    getAllPurchases: async () => {
        const response = await api.get(PURCHASE_URL);
        return response.data;
    },

    // Search purchases using optional filters
    searchPurchases: async (filters = {}) => {
        const params = Object.fromEntries(
            Object.entries(filters).filter(
                ([, value]) =>
                    value !== undefined &&
                    value !== null &&
                    value !== ""
            )
        );

        const response = await api.get(
            `${PURCHASE_URL}/search`,
            { params }
        );

        return response.data;
    },

    // Get purchase details by ID
    getPurchaseById: async (id) => {
        const response = await api.get(
            `${PURCHASE_URL}/${id}`
        );

        return response.data;
    },

    // Create a new draft purchase
    createPurchase: async (purchaseData) => {
        const response = await api.post(
            PURCHASE_URL,
            purchaseData
        );

        return response.data;
    },

    // Cancel an existing draft purchase
    cancelPurchase: async (id) => {
        const response = await api.post(
            `${PURCHASE_URL}/${id}/cancel`
        );

        return response.data;
    },

    // Receive a purchase and update inventory
    receivePurchase: async (id) => {
        const response = await api.post(
            `${PURCHASE_URL}/${id}/receive`
        );

        return response.data;
    },
};

export default purchaseService;