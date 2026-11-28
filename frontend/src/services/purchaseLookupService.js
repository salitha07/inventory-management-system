import api from "./api";

const SUPPLIER_URL = "/suppliers";
const PRODUCT_URL = "/products";

const purchaseLookupService = {
    getActiveSuppliers: async () => {
        const response = await api.get(SUPPLIER_URL, {
            params: {
                active: true,
            },
        });

        if (!Array.isArray(response.data)) {
            throw new Error("Invalid supplier response");
        }

        return response.data;
    },

    getSupplierById: async (id) => {
        const response = await api.get(
            `${SUPPLIER_URL}/${id}`
        );

        return response.data;
    },

    getActiveProducts: async ({
        search = "",
        page = 0,
        size = 20,
    } = {}) => {
        const response = await api.get(PRODUCT_URL, {
            params: {
                search: search.trim() || undefined,
                active: true,
                page,
                size,
                sortBy: "productName",
                direction: "asc",
            },
        });

        const result = response.data;

        if (
            !result?.success ||
            !Array.isArray(result.data?.content)
        ) {
            throw new Error(
                result?.message || "Invalid product response"
            );
        }

        return {
            products: result.data.content,
            totalPages: result.data.totalPages,
            totalElements: result.data.totalElements,
            page: result.data.number,
            size: result.data.size,
        };
    },

    getProductById: async (id) => {
        const response = await api.get(
            `${PRODUCT_URL}/${id}`
        );

        const result = response.data;

        if (!result?.success || !result.data) {
            throw new Error(
                result?.message || "Product not found"
            );
        }

        return result.data;
    },
};

export default purchaseLookupService;