import { useAuth } from "../context/useAuth";
import { Link, useNavigate } from "react-router-dom";
import "./Dashboard.css";

const modules = [
    {
        title: "Products",
        description: "Manage spare parts and product information.",
        icon: "📦",
        path: null
    },
    {
        title: "Inventory",
        description: "Monitor stock levels and availability.",
        icon: "📊",
        path: null
    },
    {
        title: "Suppliers",
        description: "Manage supplier information.",
        icon: "🏢",
        path: null
    },
    {
        title: "Purchases",
        description: "Manage stock purchases.",
        icon: "🛒",
        path: null
    },
    {
        title: "Sales",
        description: "Manage customer sales and invoices.",
        icon: "💰",
        path: null
    },
    {
        title: "Reports",
        description: "View business and inventory reports.",
        icon: "📈",
        path: null
    },
    {
        title: "User Management",
        description: "Manage system users and permissions.",
        icon: "👥",
        path: "/users",
        roles: ["ADMIN", "MANAGER"]
    }
];

function Dashboard() {
    const { user, logout } = useAuth();
    const navigate = useNavigate();

    const handleLogout = () => {
        logout();
        navigate("/login");
    };

    const visibleModules = modules.filter(module =>
        !module.roles || module.roles.includes(user?.role)
    );

    return (
        <div className="dashboard">

            {/* DASHBOARD HEADER */}

            <section className="dashboard-header">

                <div className="welcome-content">
                    <span className="welcome-label">
                        DASHBOARD OVERVIEW
                    </span>

                    <h1>
                        Welcome back, {user?.fullName}!
                    </h1>

                    <p>
                        Here's an overview of your Bajaj Inventory
                        Management System.
                    </p>

                    <div className="user-role-badge">
                        {user?.role?.replace("_", " ")}
                    </div>
                </div>

                <button
                    className="dashboard-logout-button"
                    onClick={handleLogout}
                >
                    <span>↪</span> Logout
                </button>

            </section>

            {/* SYSTEM MODULES */}

            <section className="dashboard-section">

                <div className="section-heading">
                    <div>
                        <h2>System Modules</h2>
                        <p>
                            Access and manage your business operations.
                        </p>
                    </div>

                    <span className="module-count">
                        {visibleModules.length} Modules
                    </span>
                </div>

                <div className="module-grid">

                    {visibleModules.map(module => {

                        const cardContent = (
                            <>
                                <div className="module-card-top">
                                    <div className="module-icon">
                                        {module.icon}
                                    </div>

                                    <span className="module-arrow">
                                        →
                                    </span>
                                </div>

                                <h3>{module.title}</h3>

                                <p>{module.description}</p>

                                <span className="module-card-link">
                                    {module.path
                                        ? "Open module →"
                                        : "Coming soon"}
                                </span>
                            </>
                        );

                        return module.path ? (
                            <Link
                                to={module.path}
                                className="module-card"
                                key={module.title}
                            >
                                {cardContent}
                            </Link>
                        ) : (
                            <div
                                className="module-card module-disabled"
                                key={module.title}
                            >
                                {cardContent}
                            </div>
                        );
                    })}

                </div>

            </section>

            {/* DASHBOARD FOOTER */}

            <footer className="dashboard-footer">
                <p>
                    © 2026 Bajaj Inventory Management System.
                    All rights reserved.
                </p>
            </footer>

        </div>
    );
}

export default Dashboard;