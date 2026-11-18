import { useState, useEffect, useCallback } from "react";
import { Link } from "react-router-dom";
import { getSalesHistory } from "../services/salesService";
import { formatPrice } from "../utils/currency";
import { formatPaymentMethod } from "../utils/cartOperations";
import "./SalesHistory.css";

export { formatPrice };

/**
 * Format ISO datetime string to user-friendly local date & time
 */
export const formatDate = (isoString) => {
  if (!isoString) return "-";
  try {
    const d = new Date(isoString);
    if (isNaN(d.getTime())) return isoString;
    return d.toLocaleString("en-IN", {
      year: "numeric",
      month: "short",
      day: "numeric",
      hour: "2-digit",
      minute: "2-digit",
    });
  } catch {
    return isoString;
  }
};

/**
 * Helper to determine CSS class for sale status badge
 */
export const getStatusBadgeClass = (status) => {
  const norm = (status || "").toUpperCase();
  if (norm === "COMPLETED") return "completed";
  if (norm === "CANCELLED") return "cancelled";
  if (norm === "REFUNDED") return "refunded";
  return "";
};

function SalesHistory() {
  const [sales, setSales] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);

  // Pagination state (Spring Data: 0-indexed page)
  const [page, setPage] = useState(0);
  const [size, setSize] = useState(20);
  const [totalPages, setTotalPages] = useState(0);
  const [totalElements, setTotalElements] = useState(0);

  // Filter state
  const [searchTerm, setSearchTerm] = useState("");
  const [statusFilter, setStatusFilter] = useState("");
  const [paymentFilter, setPaymentFilter] = useState("");

  const fetchSales = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const params = {
        page,
        size,
        sort: "saleDate,desc",
      };

      if (searchTerm.trim()) {
        params.search = searchTerm.trim();
      }
      if (statusFilter) {
        params.status = statusFilter;
      }
      if (paymentFilter) {
        params.paymentMethod = paymentFilter;
      }

      const response = await getSalesHistory(params);

      // Spring Data Page structure handling
      if (response && response.content) {
        setSales(response.content);
        setTotalPages(response.totalPages || 0);
        setTotalElements(response.totalElements || 0);
      } else if (Array.isArray(response)) {
        // Fallback for unpaged array
        setSales(response);
        setTotalPages(1);
        setTotalElements(response.length);
      } else {
        setSales([]);
        setTotalPages(0);
        setTotalElements(0);
      }
    } catch (err) {
      console.error("Failed to fetch sales history:", err);
      setError(
        err.response?.data?.message ||
          "Failed to load sales history from server. Please try again."
      );
    } finally {
      setLoading(false);
    }
  }, [page, size, searchTerm, statusFilter, paymentFilter]);

  useEffect(() => {
    fetchSales();
  }, [fetchSales]);

  const handleSearchChange = (e) => {
    setSearchTerm(e.target.value);
    setPage(0); // Reset to first page on filter change
  };

  const handleStatusChange = (e) => {
    setStatusFilter(e.target.value);
    setPage(0);
  };

  const handlePaymentChange = (e) => {
    setPaymentFilter(e.target.value);
    setPage(0);
  };

  const handleClearFilters = () => {
    setSearchTerm("");
    setStatusFilter("");
    setPaymentFilter("");
    setPage(0);
  };

  const handlePageSizeChange = (e) => {
    setSize(Number(e.target.value));
    setPage(0);
  };

  const startRecord = totalElements === 0 ? 0 : page * size + 1;
  const endRecord = Math.min((page + 1) * size, totalElements);
  const hasActiveFilters = Boolean(searchTerm.trim() || statusFilter || paymentFilter);

  return (
    <div className="sales-history-container">
      {/* Header */}
      <div className="sales-history-header">
        <div>
          <h1 className="sales-history-title">Sales History</h1>
          <p className="sales-history-subtitle">
            Browse past transactions, filter by status or payment method, and inspect invoices.
          </p>
        </div>
        <div className="sales-history-header-actions">
          <button
            type="button"
            className="sales-history-refresh-btn"
            onClick={fetchSales}
            disabled={loading}
            title="Refresh sales list"
          >
            ↻ Refresh
          </button>
          <Link to="/sales/pos" className="sales-history-new-btn">
            + New Sale
          </Link>
        </div>
      </div>

      {/* Filter and Search Bar */}
      <div className="sales-history-filters">
        <div className="sales-search-box">
          <span className="sales-search-icon" aria-hidden="true">
            🔍
          </span>
          <input
            type="text"
            className="sales-search-input"
            placeholder="Search by sale # or customer..."
            value={searchTerm}
            onChange={handleSearchChange}
            aria-label="Search transactions"
          />
        </div>

        <select
          className="sales-filter-select"
          value={statusFilter}
          onChange={handleStatusChange}
          aria-label="Filter by status"
        >
          <option value="">All Statuses</option>
          <option value="COMPLETED">Completed</option>
          <option value="CANCELLED">Cancelled</option>
          <option value="REFUNDED">Refunded</option>
        </select>

        <select
          className="sales-filter-select"
          value={paymentFilter}
          onChange={handlePaymentChange}
          aria-label="Filter by payment method"
        >
          <option value="">All Payment Methods</option>
          <option value="CASH">Cash</option>
          <option value="CARD">Card</option>
          <option value="QR_PAYMENT">QR Payment (LankaQR)</option>
          <option value="BANK_TRANSFER">Bank Transfer</option>
          <option value="CREDIT">Credit</option>
        </select>

        {hasActiveFilters && (
          <button
            type="button"
            className="sales-clear-filters-btn"
            onClick={handleClearFilters}
          >
            Clear Filters
          </button>
        )}
      </div>

      {/* Error State */}
      {error && (
        <div className="sales-history-error" role="alert">
          <div>
            <strong>Error: </strong>
            <span>{error}</span>
          </div>
          <button
            type="button"
            className="sales-history-retry-btn"
            onClick={fetchSales}
          >
            Retry
          </button>
        </div>
      )}

      {/* Sales Table */}
      <div className="sales-table-wrapper">
        <table className="sales-table" aria-label="Sales transactions list">
          <thead>
            <tr>
              <th>Sale #</th>
              <th>Date</th>
              <th>Customer</th>
              <th>Items</th>
              <th>Total</th>
              <th>Payment</th>
              <th>Status</th>
              <th>Actions</th>
            </tr>
          </thead>
          <tbody>
            {loading ? (
              <tr>
                <td colSpan={8} className="sales-state-cell" role="status">
                  <div className="sales-spinner" aria-hidden="true" />
                  <div>Loading sales history...</div>
                </td>
              </tr>
            ) : sales.length === 0 ? (
              <tr>
                <td colSpan={8} className="sales-state-cell">
                  <div className="sales-empty-title">No transactions found</div>
                  <p style={{ margin: "4px 0 12px 0" }}>
                    {hasActiveFilters
                      ? "No sales match your current search and filter criteria."
                      : "No sales recorded yet. Completed checkout sales will appear here."}
                  </p>
                  {hasActiveFilters && (
                    <button
                      type="button"
                      className="sales-action-btn"
                      onClick={handleClearFilters}
                    >
                      Clear Filters
                    </button>
                  )}
                </td>
              </tr>
            ) : (
              sales.map((sale) => (
                <tr key={sale.id || sale.saleNumber}>
                  <td>
                    <Link
                      to={`/sales/invoice/${sale.id}`}
                      className="sale-number-link"
                      title="View full invoice details"
                    >
                      {sale.saleNumber}
                    </Link>
                  </td>
                  <td>{formatDate(sale.saleDate)}</td>
                  <td>{sale.customerName || (sale.customerId ? `Customer #${sale.customerId}` : "Walk-in Customer")}</td>
                  <td>{sale.itemCount != null ? sale.itemCount : "-"}</td>
                  <td style={{ fontWeight: 600 }}>{formatPrice(sale.total)}</td>
                  <td>
                    <span className="payment-badge">
                      {formatPaymentMethod(sale.paymentMethod)}
                    </span>
                  </td>
                  <td>
                    <span
                      className={`status-badge ${getStatusBadgeClass(sale.status)}`}
                    >
                      {sale.status || "COMPLETED"}
                    </span>
                  </td>
                  <td>
                    <Link
                      to={`/sales/invoice/${sale.id}`}
                      className="sales-action-btn"
                      title="View sale invoice & receipt"
                    >
                      View Invoice →
                    </Link>
                  </td>
                </tr>
              ))
            )}
          </tbody>
        </table>
      </div>

      {/* Pagination Controls */}
      {!loading && totalElements > 0 && (
        <div className="sales-pagination-bar">
          <div className="sales-pagination-info">
            Showing {startRecord} to {endRecord} of {totalElements} sales
          </div>

          <div className="sales-pagination-controls">
            <div className="sales-page-size-selector">
              <label htmlFor="pageSizeSelect">Show:</label>
              <select
                id="pageSizeSelect"
                className="sales-page-size-select"
                value={size}
                onChange={handlePageSizeChange}
              >
                <option value={10}>10</option>
                <option value={20}>20</option>
                <option value={50}>50</option>
              </select>
            </div>

            <button
              type="button"
              className="sales-page-btn"
              disabled={page <= 0}
              onClick={() => setPage((p) => Math.max(0, p - 1))}
              aria-label="Previous page"
            >
              Previous
            </button>

            <span className="sales-page-current">
              Page {page + 1} of {Math.max(1, totalPages)}
            </span>

            <button
              type="button"
              className="sales-page-btn"
              disabled={page >= totalPages - 1}
              onClick={() => setPage((p) => p + 1)}
              aria-label="Next page"
            >
              Next
            </button>
          </div>
        </div>
      )}
    </div>
  );
}

export default SalesHistory;
