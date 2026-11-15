import { Outlet, Link } from "react-router-dom";

function MainLayout() {
  return (
    <div>
      <header>
        <h2>Bajaj Inventory</h2>

        <nav style={{ display: "flex", gap: "16px" }}>
          <Link to="/">Dashboard</Link>
          <Link to="/sales">Sales</Link>
          <Link to="/sales/pos">POS</Link>
          <Link to="/sales/history">History</Link>
        </nav>
      </header>

      <main>
        <Outlet />
      </main>
    </div>
  );
}

export default MainLayout;