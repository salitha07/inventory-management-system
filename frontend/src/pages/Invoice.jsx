import { useState, useEffect } from "react";
import { useParams, Link } from "react-router-dom";
import { getReceipt, getSaleById } from "../services/salesService";
import { formatPrice } from "../utils/currency";
import { formatPaymentMethod } from "../utils/cartOperations";
import "./Invoice.css";

function Invoice() {
  const { id } = useParams();
  const [data, setData] = useState(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);
  const [viewMode, setViewMode] = useState("standard"); // 'standard' | 'thermal'

  useEffect(() => {
    let isMounted = true;

    const fetchInvoiceData = async () => {
      setLoading(true);
      setError(null);

      try {
        // Attempt approved receipt endpoint first, fallback to standard sale detail
        try {
          const receiptData = await getReceipt(id);
          if (isMounted && receiptData) {
            setData(normalizeReceiptData(receiptData));
            setLoading(false);
            return;
          }
        } catch (receiptErr) {
          console.warn("Receipt endpoint fallback to standard sale detail:", receiptErr);
        }

        const saleData = await getSaleById(id);
        if (isMounted) {
          setData(normalizeSaleData(saleData));
        }
      } catch (err) {
        console.error("Failed to fetch invoice details:", err);
        if (isMounted) {
          setError(
            err.response?.data?.message ||
              `Invoice with ID #${id} could not be retrieved from the server.`
          );
        }
      } finally {
        if (isMounted) {
          setLoading(false);
        }
      }
    };

    if (id) {
      fetchInvoiceData();
    }

    return () => {
      isMounted = false;
    };
  }, [id]);

  const handlePrint = () => {
    window.print();
  };

  const formatDate = (dateStr) => {
    if (!dateStr) return "—";
    try {
      return new Date(dateStr).toLocaleString("en-IN", {
        year: "numeric",
        month: "short",
        day: "numeric",
        hour: "2-digit",
        minute: "2-digit",
      });
    } catch {
      return dateStr;
    }
  };

  // ──────────────────────────────────────────────
  //  Data Normalizers for Dual API Contracts
  // ──────────────────────────────────────────────

  function normalizeReceiptData(receipt) {
    return {
      storeName: receipt.storeInfo?.storeName || "Bajaj Genuine Spare Parts - Central Depot",
      storeAddress: receipt.storeInfo?.address || "45 Commercial Road, Automotive Hub",
      storePhone: receipt.storeInfo?.phone || "+91 80 2345 6789",
      storeGstin: receipt.storeInfo?.gstin || "29AAAAA0000A1Z5",
      saleNumber: receipt.receiptInfo?.saleNumber || `INV-${id}`,
      saleDate: receipt.receiptInfo?.saleDate,
      cashierName: receipt.receiptInfo?.cashierName || "Cashier",
      customerName: receipt.receiptInfo?.customerName || "Walk-in Customer",
      customerPhone: receipt.receiptInfo?.customerPhone || null,
      status: "COMPLETED",
      items: (receipt.items || []).map((it, idx) => ({
        id: idx + 1,
        partNumber: it.partNumber,
        name: it.description || it.productName,
        quantity: it.quantity,
        unitPrice: it.unitPrice,
        lineTotal: it.lineTotal,
      })),
      subtotal: receipt.totals?.subtotal,
      discount: receipt.totals?.discount || 0,
      taxIncluded: receipt.totals?.taxIncluded,
      grandTotal: receipt.totals?.grandTotal,
      paymentMethod: receipt.payment?.paymentMethod || "CASH",
      amountTendered: receipt.payment?.amountTendered,
      changeDue: receipt.payment?.changeDue,
      paymentReference: receipt.payment?.reference,
      footerNote: receipt.footerNote,
    };
  }

  function normalizeSaleData(sale) {
    const total = Number(sale.total || 0);
    const taxIncluded = (total * 18) / 118; // 18% GST calculation

    return {
      storeName: "Bajaj Genuine Spare Parts - Central Depot",
      storeAddress: "45 Commercial Road, Automotive Hub",
      storePhone: "+91 80 2345 6789",
      storeGstin: "29AAAAA0000A1Z5",
      saleNumber: sale.saleNumber || `INV-${id}`,
      saleDate: sale.saleDate,
      cashierName: sale.createdByName || "Cashier",
      customerName: sale.customerName || (sale.customerId ? `Customer #${sale.customerId}` : "Walk-in Customer"),
      customerId: sale.customerId,
      customerPhone: sale.customerPhone || null,
      status: sale.status || "COMPLETED",
      items: (sale.items || []).map((it, idx) => ({
        id: it.id || idx + 1,
        partNumber: it.partNumber || `PART-${it.productId}`,
        name: it.productName || `Product #${it.productId}`,
        quantity: it.quantity,
        unitPrice: it.unitSellingPrice,
        unitCostSnapshot: it.unitCostSnapshot,
        lineTotal: it.lineTotal,
      })),
      subtotal: sale.subtotal,
      discount: sale.discount || 0,
      taxIncluded: taxIncluded,
      grandTotal: sale.total,
      paymentMethod: sale.paymentMethod || "CASH",
      amountTendered: sale.amountTendered,
      changeDue: sale.changeDue,
      paymentReference: sale.paymentReference,
      footerNote: "Thank you for buying Bajaj Genuine Parts! Goods once sold can be exchanged within 7 days in original condition.",
    };
  }

  if (loading) {
    return (
      <div className="invoice-page">
        <div className="invoice-status-state" role="status">
          <h2>Loading Invoice & Receipt...</h2>
          <p>Retrieving authoritative transaction records from the server.</p>
        </div>
      </div>
    );
  }

  if (error || !data) {
    return (
      <div className="invoice-page">
        <div className="invoice-status-state" role="alert">
          <h2 style={{ color: "#ef4444" }}>Invoice Retrieval Failed</h2>
          <p>{error || `No transaction record found with ID #${id}.`}</p>
          <div style={{ marginTop: "20px" }}>
            <Link to="/sales/history" className="invoice-btn-print" style={{ textDecoration: "none" }}>
              ← Return to Sales History
            </Link>
          </div>
        </div>
      </div>
    );
  }

  const isThermal = viewMode === "thermal";

  return (
    <div className="invoice-page">
      {/* Top Action Bar (hidden when printed) */}
      <div className="invoice-actions-bar no-print">
        <Link to="/sales/history" className="invoice-back-link">
          ← Back to Sales History
        </Link>

        <div className="invoice-actions-right">
          <div className="invoice-mode-toggle" role="group" aria-label="Receipt format">
            <button
              type="button"
              className={`invoice-mode-btn ${viewMode === "standard" ? "active" : ""}`}
              onClick={() => setViewMode("standard")}
              title="Standard A4 Tax Invoice layout"
            >
              Tax Invoice
            </button>
            <button
              type="button"
              className={`invoice-mode-btn ${viewMode === "thermal" ? "active" : ""}`}
              onClick={() => setViewMode("thermal")}
              title="80mm Thermal Receipt layout"
            >
              80mm Receipt
            </button>
          </div>

          <button
            type="button"
            className="invoice-btn-print"
            onClick={handlePrint}
            title="Print invoice or receipt"
          >
            🖨️ Print {isThermal ? "Receipt" : "Invoice"}
          </button>
        </div>
      </div>

      {/* Main Invoice Card (Print Target) */}
      <div className={`invoice-card ${isThermal ? "thermal-mode" : ""}`} id="printable-receipt">
        {/* Store Branding Header */}
        <div className="invoice-store-header">
          <h1 className="invoice-store-name">{data.storeName}</h1>
          <p className="invoice-store-meta">{data.storeAddress}</p>
          <p className="invoice-store-meta">Tel: {data.storePhone}</p>
          <div className="invoice-store-gstin">GSTIN: {data.storeGstin}</div>
        </div>

        {/* Invoice Title & Status */}
        <div className="invoice-title-row">
          <div>
            <h2 style={{ margin: "0", fontSize: isThermal ? "14px" : "18px" }}>
              {isThermal ? "CASH MEMO / RECEIPT" : "ORIGINAL TAX INVOICE"}
            </h2>
          </div>
          <span className="invoice-badge">{data.status}</span>
        </div>

        {/* Invoice Metadata Grid (Requirements 1, 2, 3, 9) */}
        <div className="invoice-meta-grid">
          <div className="invoice-meta-item">
            <span className="invoice-meta-label">Invoice Number</span>
            <span className="invoice-meta-val mono">{data.saleNumber}</span>
          </div>

          <div className="invoice-meta-item">
            <span className="invoice-meta-label">Sale Date & Time</span>
            <span className="invoice-meta-val">{formatDate(data.saleDate)}</span>
          </div>

          <div className="invoice-meta-item">
            <span className="invoice-meta-label">Customer Details</span>
            <span className="invoice-meta-val">
              {data.customerName}
              {data.customerPhone && (
                <span style={{ display: "block", fontSize: "12px", color: "var(--text)" }}>
                  Phone: {data.customerPhone}
                </span>
              )}
            </span>
          </div>

          <div className="invoice-meta-item">
            <span className="invoice-meta-label">Cashier</span>
            <span className="invoice-meta-val">{data.cashierName}</span>
          </div>

          <div className="invoice-meta-item">
            <span className="invoice-meta-label">Payment Instrument</span>
            <span className="invoice-meta-val">{formatPaymentMethod(data.paymentMethod)}</span>
          </div>

          {data.paymentReference && (
            <div className="invoice-meta-item">
              <span className="invoice-meta-label">Payment Reference</span>
              <span className="invoice-meta-val mono">{data.paymentReference}</span>
            </div>
          )}
        </div>

        {/* Purchased Products Table (Requirements 4, 5, 10) */}
        <table className="invoice-items-table" aria-label="Purchased Products">
          <thead>
            <tr>
              <th style={{ width: "22%" }}>Part #</th>
              <th>Description</th>
              <th className="text-right" style={{ width: "10%" }}>Qty</th>
              <th className="text-right" style={{ width: "18%" }}>Unit Price</th>
              <th className="text-right" style={{ width: "18%" }}>Line Total</th>
            </tr>
          </thead>
          <tbody>
            {data.items && data.items.length > 0 ? (
              data.items.map((item, index) => (
                <tr key={item.id || index}>
                  <td className="invoice-item-sku">{item.partNumber}</td>
                  <td className="invoice-item-name">{item.name}</td>
                  <td className="text-right">{item.quantity}</td>
                  <td className="text-right">{formatPrice(item.unitPrice)}</td>
                  <td className="text-right">{formatPrice(item.lineTotal)}</td>
                </tr>
              ))
            ) : (
              <tr>
                <td colSpan={5} style={{ textAlign: "center", padding: "20px" }}>
                  No purchased items recorded for this sale.
                </td>
              </tr>
            )}
          </tbody>
        </table>

        {/* Financial Totals (Requirements 6, 7, 8) */}
        <div className="invoice-totals-wrapper">
          <div className="invoice-totals-box">
            <div className="invoice-total-row">
              <span>Gross Subtotal</span>
              <span style={{ fontWeight: "600", color: "var(--text-h)" }}>
                {formatPrice(data.subtotal)}
              </span>
            </div>

            {Number(data.discount) > 0 && (
              <div className="invoice-total-row" style={{ color: "#16a34a" }}>
                <span>Discount Applied</span>
                <span>-{formatPrice(data.discount)}</span>
              </div>
            )}

            {data.taxIncluded != null && (
              <div className="invoice-total-row" style={{ fontSize: "12px" }}>
                <span>Estimated Tax (GST incl.)</span>
                <span>{formatPrice(data.taxIncluded)}</span>
              </div>
            )}

            <div className="invoice-total-row grand-total">
              <span>Final Total Payable</span>
              <span>{formatPrice(data.grandTotal)}</span>
            </div>
          </div>
        </div>

        {/* Tender & Change Settlement Details */}
        {data.paymentMethod === "CASH" && (
          <div className="invoice-settlement-box">
            <div>
              <span style={{ color: "var(--text)", marginRight: "6px" }}>Cash Tendered:</span>
              <span style={{ fontWeight: "600" }}>{formatPrice(data.amountTendered || data.grandTotal)}</span>
            </div>
            <div>
              <span style={{ color: "var(--text)", marginRight: "6px" }}>Change Returned:</span>
              <span style={{ fontWeight: "600", color: "#15803d" }}>
                {formatPrice(data.changeDue || 0)}
              </span>
            </div>
          </div>
        )}

        {/* Receipt Footer Note */}
        {data.footerNote && (
          <div className="invoice-footer-note">
            <p style={{ margin: "0 0 4px 0" }}>{data.footerNote}</p>
            <p style={{ margin: "0", fontSize: "11px", color: "var(--text)" }}>
              This is a computer generated invoice and requires no physical signature.
            </p>
          </div>
        )}
      </div>
    </div>
  );
}

export default Invoice;
