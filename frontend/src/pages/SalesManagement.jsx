import { Link } from "react-router-dom";

function SalesManagement() {
  return (
    <div style={{ padding: "32px 24px", textAlign: "left" }}>
      <h1 style={{ fontSize: "32px", marginBottom: "8px" }}>Sales Management</h1>
      <p style={{ color: "var(--text)", marginBottom: "32px" }}>
        Manage point-of-sale transactions, view sales history, and access invoices.
      </p>

      <div
        style={{
          display: "grid",
          gridTemplateColumns: "repeat(auto-fit, minmax(260px, 1fr))",
          gap: "20px",
        }}
      >
        {/* POS Card */}
        <Link to="/sales/pos" style={{ textDecoration: "none" }}>
          <div
            style={{
              border: "1px solid var(--border)",
              borderRadius: "8px",
              padding: "24px",
              transition: "box-shadow 0.3s",
              cursor: "pointer",
            }}
          >
            <h2 style={{ margin: "0 0 8px", color: "var(--text-h)" }}>
              Point of Sale
            </h2>
            <p style={{ color: "var(--text)", fontSize: "15px" }}>
              Start a new sale — search products, build a cart, and process checkout.
            </p>
          </div>
        </Link>

        {/* Sales History Card */}
        <Link to="/sales/history" style={{ textDecoration: "none" }}>
          <div
            style={{
              border: "1px solid var(--border)",
              borderRadius: "8px",
              padding: "24px",
              transition: "box-shadow 0.3s",
              cursor: "pointer",
            }}
          >
            <h2 style={{ margin: "0 0 8px", color: "var(--text-h)" }}>
              Sales History
            </h2>
            <p style={{ color: "var(--text)", fontSize: "15px" }}>
              Browse past transactions with filters and pagination.
            </p>
          </div>
        </Link>
      </div>
    </div>
  );
}

export default SalesManagement;

