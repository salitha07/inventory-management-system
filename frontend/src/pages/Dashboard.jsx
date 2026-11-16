
import "./Dashboard.css";

const summaryCards = [
  {
    title: "Today's Sales",
    value: "—",
    description: "Sales recorded today",
    icon: "↗",
    color: "blue",
  },
  {
    title: "Monthly Sales",
    value: "—",
    description: "Sales for this month",
    icon: "▤",
    color: "purple",
  },
  {
    title: "Purchase Summary",
    value: "—",
    description: "Purchases this month",
    icon: "▣",
    color: "orange",
  },
  {
    title: "Total Products",
    value: "—",
    description: "Products in inventory",
    icon: "▦",
    color: "green",
  },
  {
    title: "Low Stock Items",
    value: "—",
    description: "Products requiring attention",
    icon: "!",
    color: "red",
  },
];

const activitySections = [
  {
    title: "Recent Sales",
    subtitle: "Latest sales transactions",
    icon: "↗",
  },
  {
    title: "Recent Purchases",
    subtitle: "Latest purchase records",
    icon: "▣",
  },
  {
    title: "Stock Movements",
    subtitle: "Latest inventory movements",
    icon: "⇄",
  },
];

function Dashboard() {
  return (
    <div className="dashboard-page">
      <div className="dashboard-heading">
        <div>
          <p className="dashboard-eyebrow">OVERVIEW</p>
          <h1>Dashboard</h1>
          <p className="dashboard-description">
            Monitor your business performance and inventory activities.
          </p>
        </div>

        <div className="dashboard-status">
          <span className="status-dot" />
          Dashboard
        </div>
      </div>

      <section className="summary-section">
        <div className="section-heading">
          <div>
            <h2>Business Summary</h2>
            <p>Key figures from your inventory system</p>
          </div>
        </div>

        <div className="summary-grid">
          {summaryCards.map((card) => (
            <article className="summary-card" key={card.title}>
              <div className="summary-card-top">
                <span className={`summary-icon ${card.color}`}>
                  {card.icon}
                </span>
                <span className="summary-menu">•••</span>
              </div>

              <p className="summary-title">{card.title}</p>
              <h3 className="summary-value">{card.value}</h3>
              <p className="summary-description">{card.description}</p>
            </article>
          ))}
        </div>
      </section>

      <section className="activity-section">
        <div className="section-heading">
          <div>
            <h2>Recent Activity</h2>
            <p>Keep track of the latest business operations</p>
          </div>
        </div>

        <div className="activity-grid">
          {activitySections.map((section) => (
            <article className="activity-card" key={section.title}>
              <div className="activity-card-heading">
                <div className="activity-icon">{section.icon}</div>
                <div>
                  <h3>{section.title}</h3>
                  <p>{section.subtitle}</p>
                </div>
              </div>

              <div className="empty-state">
                <span className="empty-state-icon">◷</span>
                <p>No activity available</p>
                <span>Records will appear here when available.</span>
              </div>
            </article>
          ))}
        </div>
      </section>

      <section className="alerts-section">
        <div className="section-heading">
          <div>
            <h2>Low Stock Alerts</h2>
            <p>Products that need inventory attention</p>
          </div>

          <span className="alert-label">Inventory Alerts</span>
        </div>

        <div className="alerts-table-wrapper">
          <table className="alerts-table">
            <thead>
              <tr>
                <th>Product</th>
                <th>SKU</th>
                <th>Current Quantity</th>
                <th>Reorder Level</th>
                <th>Status</th>
              </tr>
            </thead>

            <tbody>
              <tr>
                <td colSpan="5" className="table-empty-state">
                  <span className="empty-state-icon">✓</span>
                  <strong>No alert data available</strong>
                  <span>
                    Low stock information will appear here after inventory
                    integration.
                  </span>
                </td>
              </tr>
            </tbody>
          </table>
        </div>
      </section>

      <footer className="dashboard-footer">
        Bajaj Inventory Management System
        <span>Dashboard Module</span>
      </footer>
    </div>
  );
}

export default Dashboard;
