import { Outlet, Link } from "react-router-dom";

function MainLayout() {
  return (
    <div>
      <header>
        <h2>Bajaj Inventory</h2>

        <nav>
          <Link to="/">Dashboard</Link>
        </nav>
      </header>

      <main>
        <Outlet />
      </main>
    </div>
  );
}

export default MainLayout;