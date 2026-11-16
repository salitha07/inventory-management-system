import api from "./api";

const userService = {
    getAllUsers: async () => {
        const response = await api.get("/users");
        return response.data;
    },

    getUserById: async (id) => {
        const response = await api.get(`/users/${id}`);
        return response.data;
    },

    createUser: async (userData) => {
        const response = await api.post("/users", userData);
        return response.data;
    },

    updateUser: async (id, userData) => {
        const response = await api.put(`/users/${id}`, userData);
        return response.data;
    },

    updateUserStatus: async (id, active) => {
        const response = await api.patch(`/users/${id}/status`, {
            active
        });
        return response.data;
    }
};

export default userService;