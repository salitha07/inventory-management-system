import { useEffect, useState } from "react";
import api from "../services/api";
import AuthContext from "./authContext";

export function AuthProvider({ children }) {
    const [token, setToken] = useState(
        () => localStorage.getItem("accessToken")
    );

    const [user, setUser] = useState(null);
    const [loading, setLoading] = useState(true);

    useEffect(() => {
        const restoreSession = async () => {
            const savedToken = localStorage.getItem("accessToken");

            if (!savedToken) {
                setLoading(false);
                return;
            }

            try {
                const response = await api.get("/auth/me");
                setUser(response.data);
                setToken(savedToken);
            } catch {
                localStorage.removeItem("accessToken");
                setToken(null);
                setUser(null);
            } finally {
                setLoading(false);
            }
        };

        restoreSession();
    }, []);

    const login = (accessToken, userData) => {
        localStorage.setItem("accessToken", accessToken);
        setToken(accessToken);
        setUser(userData);
    };

    const logout = () => {
        localStorage.removeItem("accessToken");
        setToken(null);
        setUser(null);
    };

    const isAuthenticated = Boolean(token && user);

    return (
        <AuthContext.Provider
            value={{ token, user, loading, login, logout, isAuthenticated }}
        >
            {children}
        </AuthContext.Provider>
    );
}