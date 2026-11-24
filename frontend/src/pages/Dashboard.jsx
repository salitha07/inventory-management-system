import { useEffect, useState } from "react";
import "./Dashboard.css";
import {
  getDashboardSummary,
  getLowStockAlerts,
  getRecentActivity,
} from "../services/dashboardService";

const initialSummary = {
  todaySales: null,
  monthlySales: null,
  purchaseSummary: null,
  totalProducts: null,
  lowStockCount: null,
};

const formatCurrency = (value) => {
  if (value === null || value === undefined) return "—";

  return `Rs. ${Number(value).toLocaleString("en-LK", {
    minimumFractionDigits: 2,
    maximumFractionDigits: 2,
  })}`;
};

const formatDate = (value) => {
  if (!value) return "—";

  return new Date(value).toLocaleString("en-LK", {
    dateStyle: "medium",
    timeStyle: "short",
  });
};

function Dashboard() {
  const [summary, setSummary] = useState(initialSummary);
  const [recentSales, setRecentSales] = useState([]);
  const [summaryLoading, setSummaryLoading] = useState(true);
  const [activityLoading, setActivityLoading] = useState(true);
  const [summaryError, setSummaryError] = useState("");
  const [activityError, setActivityError] = useState("");
  const [lowStockUnavailable, setLowStockUnavailable] = useState(false);

  useEffect(() => {
    const loadDashboard = async () => {
      try {
        const data = await getDashboardSummary();
        setSummary(data);
      } catch (error) {
        console.error("Dashboard summary error:", error);
        setSummaryError("Unable to load dashboard summary.");
      } finally {
        setSummaryLoading(false);
      }

      try {
        const activities = await getRecentActivity();
        setRecentSales(Array.isArray(activities) ? activities : []);
      } catch (error) {
        console.error("Recent activity error:", error);
        setActivityError("Unable to load recent sales.");
      } finally {
        setActivityLoading(false);
      }

      try {
        await getLowStockAlerts();
      } catch (error) {
        console.error("Low-stock alerts unavailable:", error);
        setLowStockUnavailable(true);
      }
    };

    loadDashboard();
  }, []);

  const summaryCards = [
    {
      title: "Today's Sales",
      value: formatCurrency(summary.todaySales),
      description: "Sales recorded today",
      icon: "$",
      color: "blue",
    },
    {
      title: "Monthly Sales",
      value: formatCurrency(summary.monthlySales),
      description: "Sales for this month",
      icon: "M",
      color: "purple",
    },
    {
      title: "Purchase Summary",
      value: formatCurrency(summary.purchaseSummary),
      description: "Purchases this month",
      icon: "P",
      color: "orange",
    },
    {
      title: "Total Products",
      value:
        summary.totalProducts === null
          ? "—"
          : summary.totalProducts.toLocaleString(),
      description: "Products in inventory",
      icon: "T",
      color: "green",
    },
    {
      title: "Low Stock Items",
      value:
        summary.lowStockCount === null
          ? "—"
          : summary.lowStockCount.toLocaleString(),
      description: "Products requiring attention",
      icon: "!",
      color: "red",
    },
  ];

  const activitySections = [
    {
      title: "Recent Sales",
      subtitle: "Latest sales transactions",
      icon: "$",
      type: "sales",
    },
    {
      title: "Recent Purchases",
      subtitle: "Latest purchase records",
      icon: "P",
      type: "purchases",
    },
    {
      title: "Stock Movements",
      subtitle: "Latest inventory movements",
      icon: "↔",
      type: "stock",
    },
  ];

  const renderActivity = (section) => {
    if (section.type === "sales") {
      if (activityLoading) {
        return <div className="empty-state"><p>Loading recent sales...</p></div>;
      }

      if (activityError) {
        return <div className="empty-state"><p>{activityError}</p></div>;
      }

      if (recentSales.length === 0) {
        return (
          <div className="empty-state">
            <p>No recent sales</p>
            <span>Sales records will appear here when available.</span>
          </div>
        );
      }

      return (
        <div className="dashboard-activity-list">
          {recentSales.map((activity) => (
            <div className="dashboard-activity-item" key={activity.id}>
              <div>
                <strong>{activity.referenceNumber || "Sale"}</strong>
                <p>{activity.description || "Sales transaction"}</p>
                <span>{formatDate(activity.activityDate)}</span>
              </div>
              <strong>{formatCurrency(activity.amount)}</strong>
            </div>
          ))}
        </div>
      );
    }

    return (
      <div className="empty-state">
        <p>Currently unavailable</p>
        <span>
          {section.type === "purchases"
            ? "Purchase module integration is not available yet."
            : "Inventory integration is not available yet."}
        </span>
      </div>
    );
  };

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

        {summaryError && (
          <p role="alert">{summaryError}</p>
        )}

        <div className="summary-grid">
          {summaryCards.map((card) => (
            <article className="summary-card" key={card.title}>
              <div className="summary-card-top">
                <span className={`summary-icon ${card.color}`}>
                  {card.icon}
                </span>
                <span className="summary-menu">...</span>
              </div>

              <p className="summary-title">{card.title}</p>
              <h3 className="summary-value">
                {summaryLoading ? "Loading..." : card.value}
              </h3>
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

              {renderActivity(section)}
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
                  <strong>
                    {lowStockUnavailable
                      ? "Low-stock alerts unavailable"
                      : "Loading low-stock alerts..."}
                  </strong>
                  <span>
                    {lowStockUnavailable
                      ? "Inventory migration is not active yet."
                      : "Checking inventory data."}
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
