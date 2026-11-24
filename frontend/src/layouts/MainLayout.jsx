import { Outlet, NavLink } from "react-router-dom";
import { useAuth } from "../context/useAuth";
import "./MainLayout.css";

function MainLayout() {
    const { user } = useAuth();

    return (
        <div className="main-layout">
            <header className="app-header">

                {/* BRANDING */}
                <div className="app-brand">
                    <div className="app-brand-icon">➤</div>

                    <div className="app-brand-text">
                        <h2>BAJAJ</h2>
                        <span>Inventory Management System</span>
                    </div>
                </div>

                {/* NAVIGATION */}
                <nav className="app-navigation">

                    {/* Dashboard */}
                    <NavLink
                        to="/"
                        end
                        className={({ isActive }) =>
                            isActive ? "nav-link active" : "nav-link"
                        }
                    >
                        <span>▦</span>
                        Dashboard
                    </NavLink>

                    {/* User Management */}
                    {["ADMIN", "MANAGER"].includes(user?.role) && (
                        <NavLink
                            to="/users"
                            className={({ isActive }) =>
                                isActive ? "nav-link active" : "nav-link"
                            }
                        >
                            <span>👥</span>
                            User Management
                        </NavLink>
                    )}

                    {/* Audit Logs - ADMIN ONLY */}
                    {user?.role === "ADMIN" && (
                        <NavLink
                            to="/audit-logs"
                            className={({ isActive }) =>
                                isActive ? "nav-link active" : "nav-link"
                            }
                        >
                            <span>📋</span>
                            Audit Logs
                        </NavLink>
                    )}

                    {/* Sales Management */}
                    <NavLink
                        to="/sales"
                        className={({ isActive }) =>
                            isActive ? "nav-link active" : "nav-link"
                        }
                    >
                        <span>💰</span>
                        Sales
                    </NavLink>

                    {/* Point of Sale */}
                    <NavLink
                        to="/sales/pos"
                        className={({ isActive }) =>
                            isActive ? "nav-link active" : "nav-link"
                        }
                    >
                        <span>🛒</span>
                        POS
                    </NavLink>

                    {/* Sales History */}
                    <NavLink
                        to="/sales/history"
                        className={({ isActive }) =>
                            isActive ? "nav-link active" : "nav-link"
                        }
                    >
                        <span>📋</span>
                        Sales History
                    </NavLink>

                </nav>

                {/* USER ROLE */}
                <div className="header-user">
                    <span className="header-user-indicator"></span>
                    <span>{user?.role?.replace("_", " ")}</span>
                </div>

            </header>

            {/* PAGE CONTENT */}
            <main className="app-content">
                <Outlet />
            </main>
        </div>
    );
}

export default MainLayout;