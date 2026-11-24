
import api from "./api";

const auditService = {
    getAllAuditLogs: async (page = 0, size = 10) => {
        const response = await api.get("/audit", {
            params: {
                page,
                size,
            },
        });

        return response.data;
    },
};

export default auditService;
