import { Outlet, NavLink } from "react-router-dom";
import { useAuth } from "../context/useAuth";
import "./MainLayout.css";

function MainLayout() {
    const { user } = useAuth();

    return (
        <div className="main-layout">

            {/* APPLICATION HEADER */}

            <header className="app-header">

                {/* BRANDING */}

                <div className="app-brand">
                    <div className="app-brand-icon">
                        ➤
                    </div>

                    <div className="app-brand-text">
                        <h2>BAJAJ</h2>
                        <span>Inventory Management System</span>
                    </div>
                </div>

                {/* NAVIGATION */}

                <nav className="app-navigation">

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

                </nav>

                {/* USER ROLE */}

                <div className="header-user">
                    <span className="header-user-indicator"></span>

                    <span>
                        {user?.role?.replace("_", " ")}
                    </span>
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