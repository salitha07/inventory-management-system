import { Outlet, Link, useNavigate } from "react-router-dom";
import { useAuth } from "../context/useAuth";

function MainLayout() {
    const { user, logout } = useAuth();
    const navigate = useNavigate();

    const handleLogout = () => {
        logout();
        navigate("/login");
    };

    return (
        <div>
            <header>
                <h2>Bajaj Inventory Management System</h2>

                <nav>
                    <Link to="/">Dashboard</Link>
                </nav>

                <div>
                    <p>{user?.fullName}</p>
                    <p>{user?.email}</p>
                    <p>Role: {user?.role}</p>

                    <button onClick={handleLogout}>
                        Logout
                    </button>
                </div>
            </header>

            <main>
                <Outlet />
            </main>
        </div>
    );
}

export default MainLayout;