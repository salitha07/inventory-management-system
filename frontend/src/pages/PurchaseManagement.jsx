import { useEffect, useRef, useState } from "react";
import { useAuth } from "../context/useAuth";
import purchaseService from "../services/purchaseService";
import purchaseLookupService from "../services/purchaseLookupService";
import PurchaseForm from "../components/purchase/PurchaseForm";

import {
  PURCHASE_STATUS,
  canAccessPurchases,
  canCreatePurchase,
  canReceivePurchase,
  canCancelPurchase,
} from "../utils/purchaseOperations";

import { formatPrice } from "../utils/currency";

const INITIAL_FILTERS = {
  purchaseNumber: "",
  supplierId: "",
  status: "",
  fromDate: "",
  toDate: "",
};

function getErrorMessage(error, fallback) {
  return error.response?.data?.message || error.message || fallback;
}

function PurchaseStatus({ status }) {
  return (
    <span
      className={`purchase-status purchase-status-${String(
        status
      ).toLowerCase()}`}
    >
      {status}
    </span>
  );
}

export default function PurchaseManagement() {
  const { user } = useAuth();

  const role = user?.role;
  const hasAccess = canAccessPurchases(role);

  const [purchases, setPurchases] = useState([]);
  const [suppliers, setSuppliers] = useState([]);

  const [filters, setFilters] = useState({
    ...INITIAL_FILTERS,
  });

  const [appliedFilters, setAppliedFilters] = useState({
    ...INITIAL_FILTERS,
  });

  const [loading, setLoading] = useState(true);
  const [showForm, setShowForm] = useState(false);
  const [refreshCount, setRefreshCount] = useState(0);

  const [error, setError] = useState("");
  const [supplierError, setSupplierError] = useState("");
  const [success, setSuccess] = useState("");
  const [filterError, setFilterError] = useState("");
  const [processingId, setProcessingId] = useState(null);

  const actionLock = useRef(false);

  useEffect(() => {
    if (!hasAccess) return;

    let cancelled = false;

    async function loadPurchases() {
      try {
        const activeFilters = Object.fromEntries(
          Object.entries(appliedFilters).filter(
            ([, value]) => value !== ""
          )
        );

        const result =
          Object.keys(activeFilters).length > 0
            ? await purchaseService.searchPurchases(
                activeFilters
              )
            : await purchaseService.getAllPurchases();

        if (!Array.isArray(result)) {
          throw new Error("Invalid purchase response.");
        }

        if (!cancelled) {
          const sortedPurchases = [...result].sort(
            (first, second) =>
              String(second.purchaseDate).localeCompare(
                String(first.purchaseDate)
              ) || Number(second.id) - Number(first.id)
          );

          setPurchases(sortedPurchases);
          setError("");
        }
      } catch (requestError) {
        if (!cancelled) {
          setPurchases([]);

          setError(
            getErrorMessage(
              requestError,
              "Unable to load purchases."
            )
          );
        }
      } finally {
        if (!cancelled) {
          setLoading(false);
        }
      }
    }

    loadPurchases();

    return () => {
      cancelled = true;
    };
  }, [hasAccess, appliedFilters, refreshCount]);

  useEffect(() => {
    if (!hasAccess) return;

    let cancelled = false;

    async function loadSuppliers() {
      try {
        const result =
          await purchaseLookupService.getActiveSuppliers();

        if (!cancelled) {
          setSuppliers(result);
          setSupplierError("");
        }
      } catch (requestError) {
        if (!cancelled) {
          setSupplierError(
            getErrorMessage(
              requestError,
              "Unable to load supplier names."
            )
          );
        }
      }
    }

    loadSuppliers();

    return () => {
      cancelled = true;
    };
  }, [hasAccess, refreshCount]);

  function getSupplierName(supplierId) {
    const supplier = suppliers.find(
      (entry) => String(entry.id) === String(supplierId)
    );

    return supplier?.supplierName || `Supplier #${supplierId}`;
  }

  function refreshPurchases() {
    setLoading(true);
    setError("");
    setRefreshCount((previous) => previous + 1);
  }

  function updateFilter(field, value) {
    setFilters((previous) => ({
      ...previous,
      [field]: value,
    }));

    setFilterError("");
  }

  function applyFilters(event) {
    event.preventDefault();

    if (
      filters.fromDate &&
      filters.toDate &&
      filters.fromDate > filters.toDate
    ) {
      setFilterError(
        "From date cannot be later than to date."
      );

      return;
    }

    setLoading(true);
    setError("");
    setFilterError("");

    setAppliedFilters({
      ...filters,
      purchaseNumber: filters.purchaseNumber.trim(),
    });
  }

  function resetFilters() {
    setFilters({ ...INITIAL_FILTERS });
    setAppliedFilters({ ...INITIAL_FILTERS });
    setFilterError("");
    setLoading(true);
    setError("");
  }

  function handlePurchaseCreated(purchase) {
    setShowForm(false);
    setError("");

    setSuccess(
      purchase?.purchaseNumber
        ? `Purchase ${purchase.purchaseNumber} created successfully.`
        : "Purchase created successfully."
    );

    resetFilters();
    refreshPurchases();
  }

  async function handlePurchaseAction(purchase, action) {
    if (actionLock.current) return;

    const isReceive = action === "receive";

    const permitted = isReceive
      ? canReceivePurchase(role, purchase.status)
      : canCancelPurchase(role, purchase.status);

    if (!permitted) return;

    const message = isReceive
      ? `Receive purchase ${purchase.purchaseNumber} and update inventory stock?`
      : `Cancel purchase ${purchase.purchaseNumber}?`;

    if (!window.confirm(message)) return;

    actionLock.current = true;

    setProcessingId(purchase.id);
    setError("");
    setSuccess("");

    try {
      if (isReceive) {
        await purchaseService.receivePurchase(purchase.id);

        setSuccess(
          `Purchase ${purchase.purchaseNumber} received successfully.`
        );
      } else {
        await purchaseService.cancelPurchase(purchase.id);

        setSuccess(
          `Purchase ${purchase.purchaseNumber} cancelled successfully.`
        );
      }

      refreshPurchases();
    } catch (requestError) {
      setError(
        getErrorMessage(
          requestError,
          isReceive
            ? "Unable to receive purchase."
            : "Unable to cancel purchase."
        )
      );
    } finally {
      actionLock.current = false;
      setProcessingId(null);
    }
  }

  if (!hasAccess) {
    return (
      <section className="purchase-management">
        <div className="purchase-alert-error" role="alert">
          You do not have permission to access Purchase Management.
        </div>
      </section>
    );
  }

  const summary = {
    total: purchases.length,
    draft: purchases.filter(
      (purchase) => purchase.status === PURCHASE_STATUS.DRAFT
    ).length,
    received: purchases.filter(
      (purchase) =>
        purchase.status === PURCHASE_STATUS.RECEIVED
    ).length,
    cancelled: purchases.filter(
      (purchase) =>
        purchase.status === PURCHASE_STATUS.CANCELLED
    ).length,
  };

  return (
    <section className="purchase-management">
      <header className="purchase-page-header">
        <div>
          <h1>Purchase Management</h1>

          <p>
            Manage supplier purchases, invoices and stock receipts.
          </p>
        </div>

        <div className="purchase-header-actions">
          <button
            type="button"
            className="purchase-secondary-button"
            onClick={refreshPurchases}
            disabled={loading || processingId !== null}
          >
            Refresh
          </button>

          {canCreatePurchase(role) && !showForm && (
            <button
              type="button"
              className="purchase-primary-button"
              onClick={() => {
                setShowForm(true);
                setSuccess("");
              }}
              disabled={processingId !== null}
            >
              + New Purchase
            </button>
          )}
        </div>
      </header>

      {error && (
        <div className="purchase-alert-error" role="alert">
          {error}
        </div>
      )}

      {supplierError && (
        <div className="purchase-alert-error" role="alert">
          {supplierError}
        </div>
      )}

      {success && (
        <div className="purchase-alert-success" role="status">
          {success}
        </div>
      )}

      {showForm && (
        <PurchaseForm
          onSuccess={handlePurchaseCreated}
          onCancel={() => setShowForm(false)}
        />
      )}

      <section
        className="purchase-summary-grid"
        aria-label="Filtered purchase summary"
      >
        <div className="purchase-summary-card">
          <span>Total Purchases</span>
          <strong>{loading ? "—" : summary.total}</strong>
        </div>

        <div className="purchase-summary-card">
          <span>Draft Purchases</span>
          <strong>{loading ? "—" : summary.draft}</strong>
        </div>

        <div className="purchase-summary-card">
          <span>Received Purchases</span>
          <strong>{loading ? "—" : summary.received}</strong>
        </div>

        <div className="purchase-summary-card">
          <span>Cancelled Purchases</span>
          <strong>{loading ? "—" : summary.cancelled}</strong>
        </div>
      </section>

      <section className="purchase-filter-section">
        <h2>Search and Filter Purchases</h2>

        <form
          className="purchase-filter-form"
          onSubmit={applyFilters}
        >
          <div className="purchase-field">
            <label htmlFor="filter-purchase-number">
              Purchase Number
            </label>

            <input
              id="filter-purchase-number"
              type="search"
              value={filters.purchaseNumber}
              placeholder="Search purchase number"
              onChange={(event) =>
                updateFilter(
                  "purchaseNumber",
                  event.target.value
                )
              }
            />
          </div>

          <div className="purchase-field">
            <label htmlFor="filter-supplier">
              Supplier
            </label>

            <select
              id="filter-supplier"
              value={filters.supplierId}
              onChange={(event) =>
                updateFilter(
                  "supplierId",
                  event.target.value
                )
              }
            >
              <option value="">All Suppliers</option>

              {suppliers.map((supplier) => (
                <option
                  key={supplier.id}
                  value={supplier.id}
                >
                  {supplier.supplierName}
                </option>
              ))}
            </select>
          </div>

          <div className="purchase-field">
            <label htmlFor="filter-status">
              Status
            </label>

            <select
              id="filter-status"
              value={filters.status}
              onChange={(event) =>
                updateFilter("status", event.target.value)
              }
            >
              <option value="">All Statuses</option>
              <option value="DRAFT">Draft</option>
              <option value="RECEIVED">Received</option>
              <option value="CANCELLED">Cancelled</option>
            </select>
          </div>

          <div className="purchase-field">
            <label htmlFor="filter-from-date">
              From Date
            </label>

            <input
              id="filter-from-date"
              type="date"
              value={filters.fromDate}
              onChange={(event) =>
                updateFilter("fromDate", event.target.value)
              }
            />
          </div>

          <div className="purchase-field">
            <label htmlFor="filter-to-date">
              To Date
            </label>

            <input
              id="filter-to-date"
              type="date"
              value={filters.toDate}
              onChange={(event) =>
                updateFilter("toDate", event.target.value)
              }
            />
          </div>

          <div className="purchase-filter-actions">
            <button
              type="submit"
              className="purchase-primary-button"
              disabled={loading}
            >
              Search
            </button>

            <button
              type="button"
              className="purchase-secondary-button"
              onClick={resetFilters}
              disabled={loading}
            >
              Reset
            </button>
          </div>
        </form>

        {filterError && (
          <p className="purchase-field-error" role="alert">
            {filterError}
          </p>
        )}
      </section>

      <section className="purchase-list-section">
        <div className="purchase-list-header">
          <h2>Purchase Records</h2>

          <span>
            {loading
              ? "Loading..."
              : `${purchases.length} record(s)`}
          </span>
        </div>

        {loading ? (
          <div className="purchase-loading" role="status">
            Loading purchases...
          </div>
        ) : (
          <div className="purchase-table-wrapper">
            <table className="purchase-list-table">
              <thead>
                <tr>
                  <th>Purchase Number</th>
                  <th>Supplier</th>
                  <th>Invoice Number</th>
                  <th>Purchase Date</th>
                  <th>Status</th>
                  <th>Total Amount</th>
                  <th>Actions</th>
                </tr>
              </thead>

              <tbody>
                {purchases.length === 0 ? (
                  <tr>
                    <td
                      colSpan={7}
                      className="purchase-empty-state"
                    >
                      No purchase records found.
                    </td>
                  </tr>
                ) : (
                  purchases.map((purchase) => (
                    <tr key={purchase.id}>
                      <td>
                        <strong>
                          {purchase.purchaseNumber}
                        </strong>
                      </td>

                      <td>
                        {getSupplierName(
                          purchase.supplierId
                        )}
                      </td>

                      <td>
                        {purchase.supplierInvoiceNumber}
                      </td>

                      <td>{purchase.purchaseDate}</td>

                      <td>
                        <PurchaseStatus
                          status={purchase.status}
                        />
                      </td>

                      <td>
                        {formatPrice(purchase.total)}
                      </td>

                      <td>
                        <div className="purchase-row-actions">
                          {canReceivePurchase(
                            role,
                            purchase.status
                          ) && (
                            <button
                              type="button"
                              className="purchase-receive-button"
                              disabled={
                                processingId !== null ||
                                showForm
                              }
                              onClick={() =>
                                handlePurchaseAction(
                                  purchase,
                                  "receive"
                                )
                              }
                            >
                              {processingId === purchase.id
                                ? "Processing..."
                                : "Receive"}
                            </button>
                          )}

                          {canCancelPurchase(
                            role,
                            purchase.status
                          ) && (
                            <button
                              type="button"
                              className="purchase-cancel-button"
                              disabled={
                                processingId !== null ||
                                showForm
                              }
                              onClick={() =>
                                handlePurchaseAction(
                                  purchase,
                                  "cancel"
                                )
                              }
                            >
                              Cancel
                            </button>
                          )}

                          {!canReceivePurchase(
                            role,
                            purchase.status
                          ) &&
                            !canCancelPurchase(
                              role,
                              purchase.status
                            ) && <span>—</span>}
                        </div>
                      </td>
                    </tr>
                  ))
                )}
              </tbody>
            </table>
          </div>
        )}
      </section>
    </section>
  );
}