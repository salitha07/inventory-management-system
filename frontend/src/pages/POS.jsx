import { useState, useEffect } from "react";
import { searchProducts } from "../services/productService";
import { createSale } from "../services/salesService";
import { useDebounce } from "../hooks/useDebounce";
import { useCart } from "../hooks/useCart";
import {
  isValidQuantity,
  calculateLineTotal,
  calculateChangeDue,
  validateCheckoutForm,
  PAYMENT_METHODS,
  formatPaymentMethod,
} from "../utils/cartOperations";
import { formatPrice } from "../utils/currency";
import "./POS.css";

function CartItemRow({
  item,
  onUpdateQuantity,
  onIncrease,
  onDecrease,
  onRemove,
  formatPrice,
}) {
  const [prevQuantity, setPrevQuantity] = useState(item.quantity);
  const [inputVal, setInputVal] = useState(String(item.quantity));

  if (item.quantity !== prevQuantity) {
    setPrevQuantity(item.quantity);
    setInputVal(String(item.quantity));
  }

  const handleInputChange = (e) => {
    const val = e.target.value;
    setInputVal(val);

    if (isValidQuantity(val, item.availableQuantity)) {
      onUpdateQuantity(item.id, parseInt(val, 10));
    }
  };

  const handleBlur = () => {
    if (!isValidQuantity(inputVal, item.availableQuantity)) {
      setInputVal(String(item.quantity));
    } else {
      onUpdateQuantity(item.id, parseInt(inputVal, 10));
    }
  };

  const handleKeyDown = (e) => {
    if (e.key === "Enter") {
      e.target.blur();
    }
  };

  const lineTotal = calculateLineTotal(item.unitPrice, item.quantity);
  const isMaxStock =
    item.availableQuantity !== undefined &&
    item.availableQuantity !== null &&
    item.quantity >= item.availableQuantity;

  return (
    <div className="pos-cart-item">
      <div className="pos-cart-item-row-top">
        <div className="pos-cart-item-info">
          <span className="pos-cart-item-name">{item.name}</span>
          <span className="pos-cart-item-sku">SKU: {item.sku}</span>
        </div>
        <button
          type="button"
          className="pos-btn-remove"
          onClick={() => onRemove(item.id)}
          title="Remove item"
          aria-label={`Remove ${item.name}`}
        >
          &times;
        </button>
      </div>

      <div className="pos-cart-item-row-bottom">
        <span className="pos-cart-item-unit-price">
          {formatPrice(item.unitPrice)} each
        </span>

        <div className="pos-qty-controls">
          <button
            type="button"
            className="pos-qty-btn"
            onClick={() => onDecrease(item.id)}
            disabled={item.quantity <= 1}
            title="Decrease quantity"
            aria-label="Decrease quantity"
          >
            -
          </button>
          <input
            type="text"
            inputMode="numeric"
            pattern="[0-9]*"
            className="pos-qty-input"
            value={inputVal}
            onChange={handleInputChange}
            onBlur={handleBlur}
            onKeyDown={handleKeyDown}
            title="Enter quantity"
            aria-label={`Quantity for ${item.name}`}
          />
          <button
            type="button"
            className="pos-qty-btn"
            onClick={() => onIncrease(item.id)}
            disabled={isMaxStock}
            title={isMaxStock ? "Maximum available stock reached" : "Increase quantity"}
            aria-label="Increase quantity"
          >
            +
          </button>
        </div>

        <span className="pos-cart-line-total">{formatPrice(lineTotal)}</span>
      </div>
    </div>
  );
}

function CheckoutModal({
  isOpen,
  onClose,
  cart,
  subtotal,
  discount,
  totalAmount,
  totalItemsCount,
  formatPrice,
  onClearCart,
  onCheckoutSuccess,
}) {
  const [isWalkIn, setIsWalkIn] = useState(true);
  const [customerId, setCustomerId] = useState("");
  const [paymentMethod, setPaymentMethod] = useState("CASH");
  const [amountTendered, setAmountTendered] = useState(String(totalAmount));
  const [paymentReference, setPaymentReference] = useState("");
  const [formErrors, setFormErrors] = useState({});
  const [step, setStep] = useState("form"); // 'form' | 'confirm' | 'success'
  const [validatedPayload, setValidatedPayload] = useState(null);

  // Async submission states
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [submissionError, setSubmissionError] = useState(null);
  const [completedSale, setCompletedSale] = useState(null);

  // Sync amount tendered with totalAmount when modal opens or total changes
  const [prevTotal, setPrevTotal] = useState(totalAmount);
  if (totalAmount !== prevTotal) {
    setPrevTotal(totalAmount);
    if (paymentMethod === "CASH") {
      setAmountTendered(String(totalAmount));
    }
  }

  if (!isOpen) return null;

  const changeDue = calculateChangeDue(amountTendered, totalAmount);

  const handleWalkInToggle = (walkIn) => {
    setIsWalkIn(walkIn);
    if (walkIn && paymentMethod === "CREDIT") {
      setPaymentMethod("CASH"); // Reset if credit was selected
    }
    setFormErrors((prev) => ({ ...prev, customerId: undefined }));
    setSubmissionError(null);
  };

  const handlePaymentMethodChange = (method) => {
    setPaymentMethod(method);
    if (method === "CASH" && (!amountTendered || Number(amountTendered) < totalAmount)) {
      setAmountTendered(String(totalAmount));
    }
    setFormErrors((prev) => ({ ...prev, paymentMethod: undefined, amountTendered: undefined }));
    setSubmissionError(null);
  };

  const handleReviewCheckout = (e) => {
    e.preventDefault();

    const validation = validateCheckoutForm({
      cart,
      isWalkIn,
      customerId,
      paymentMethod,
      amountTendered,
      paymentReference,
      total: totalAmount,
      subtotal,
      discount,
    });

    if (!validation.isValid) {
      setFormErrors(validation.errors);
      return;
    }

    setFormErrors({});
    setSubmissionError(null);
    setValidatedPayload(validation.payload);
    setStep("confirm");
  };

  const handleFinalConfirm = async () => {
    if (isSubmitting || !validatedPayload) return;

    setIsSubmitting(true);
    setSubmissionError(null);

    try {
      const response = await createSale(validatedPayload);
      setCompletedSale(response);
      setStep("success");
      if (onCheckoutSuccess) {
        onCheckoutSuccess(response);
      }
    } catch (err) {
      console.error("POS Checkout submission error:", err);
      const status = err.response?.status;
      const data = err.response?.data;

      let title = "Checkout Failed";
      let message = "An unexpected error occurred while communicating with the server. Please try again.";

      if (status === 409) {
        title = "Insufficient Stock (409 Conflict)";
        message = data?.message || "One or more products have insufficient available stock. Please adjust cart quantities.";
      } else if (status === 404) {
        title = "Product Not Found (404)";
        message = data?.message || "A part in your cart was not found or has been discontinued in the active catalog.";
      } else if (status === 400) {
        title = "Validation Error (400 Bad Request)";
        if (data?.validationErrors && data.validationErrors.length > 0) {
          message = data.validationErrors.map((v) => `${v.field}: ${v.message}`).join("; ");
        } else {
          message = data?.message || "Invalid checkout request data.";
        }
      } else if (status === 403) {
        title = "Permission Denied (403)";
        message = data?.message || "Action not allowed. Store Manager override authorization required.";
      } else if (!err.response) {
        title = "Network Error";
        message = "Unable to connect to the backend server. Please verify your network connection or server availability.";
      } else if (data?.message) {
        message = data.message;
      }

      setSubmissionError({ status, title, message });
    } finally {
      setIsSubmitting(false);
    }
  };

  const handleFinish = () => {
    // Clear cart strictly after confirmed transaction success (Requirement 8)
    onClearCart();
    onClose();
    setStep("form");
    setValidatedPayload(null);
    setCompletedSale(null);
    setSubmissionError(null);
  };

  return (
    <div className="pos-modal-overlay" role="dialog" aria-modal="true" aria-labelledby="checkout-modal-title">
      <div className="pos-modal-content">
        <div className="pos-modal-header">
          <h2 id="checkout-modal-title">
            {step === "form" && "Checkout & Settlement"}
            {step === "confirm" && "Confirm Sale Transaction"}
            {step === "success" && "Sale Invoice Completed"}
          </h2>
          <button
            type="button"
            className="pos-modal-close-btn"
            onClick={onClose}
            aria-label="Close modal"
            disabled={isSubmitting}
          >
            &times;
          </button>
        </div>

        {/* STEP 1: FORM ENTRY */}
        {step === "form" && (
          <form onSubmit={handleReviewCheckout} className="pos-modal-body">
            {/* Customer Selection */}
            <div className="pos-form-section">
              <label className="pos-form-label">Customer Type</label>
              <div className="pos-radio-pills">
                <button
                  type="button"
                  className={`pos-radio-pill ${isWalkIn ? "selected" : ""}`}
                  onClick={() => handleWalkInToggle(true)}
                >
                  Walk-in Customer (Default)
                </button>
                <button
                  type="button"
                  className={`pos-radio-pill ${!isWalkIn ? "selected" : ""}`}
                  onClick={() => handleWalkInToggle(false)}
                >
                  Registered Account / Garage
                </button>
              </div>

              {!isWalkIn && (
                <div style={{ marginTop: "8px" }}>
                  <label htmlFor="customer-id-input" className="pos-form-label" style={{ fontSize: "13px" }}>
                    Customer ID / Account Reference *
                  </label>
                  <input
                    id="customer-id-input"
                    type="number"
                    min="1"
                    className={`pos-input-text ${formErrors.customerId ? "error" : ""}`}
                    placeholder="e.g. 42 (Registered customer ID)"
                    value={customerId}
                    onChange={(e) => {
                      setCustomerId(e.target.value);
                      if (formErrors.customerId) {
                        setFormErrors((prev) => ({ ...prev, customerId: undefined }));
                      }
                    }}
                  />
                  {formErrors.customerId && (
                    <div className="pos-field-error">{formErrors.customerId}</div>
                  )}
                </div>
              )}
            </div>

            {/* Payment Method Selection */}
            <div className="pos-form-section">
              <label className="pos-form-label">Payment Method</label>
              <div className="pos-payment-grid">
                {PAYMENT_METHODS.map((method) => {
                  const isCreditDisabled = isWalkIn && method.value === "CREDIT";
                  const isSelected = paymentMethod === method.value;

                  return (
                    <button
                      key={method.value}
                      type="button"
                      disabled={isCreditDisabled}
                      className={`pos-payment-option ${isSelected ? "selected" : ""} ${
                        isCreditDisabled ? "disabled" : ""
                      }`}
                      onClick={() => handlePaymentMethodChange(method.value)}
                      title={isCreditDisabled ? "Credit requires a registered customer" : method.label}
                    >
                      {method.label}
                    </button>
                  );
                })}
              </div>
              {formErrors.paymentMethod && (
                <div className="pos-field-error">{formErrors.paymentMethod}</div>
              )}
            </div>

            {/* Cash Payment Tendered & Change Due */}
            {paymentMethod === "CASH" && (
              <div className="pos-form-section">
                <label htmlFor="amount-tendered-input" className="pos-form-label">
                  Amount Tendered (Rs.) *
                </label>
                <input
                  id="amount-tendered-input"
                  type="number"
                  step="0.01"
                  min="0"
                  className={`pos-input-text ${formErrors.amountTendered ? "error" : ""}`}
                  value={amountTendered}
                  onChange={(e) => {
                    setAmountTendered(e.target.value);
                    if (formErrors.amountTendered) {
                      setFormErrors((prev) => ({ ...prev, amountTendered: undefined }));
                    }
                  }}
                  placeholder="0.00"
                />
                {formErrors.amountTendered && (
                  <div className="pos-field-error">{formErrors.amountTendered}</div>
                )}

                {/* Quick Cash Presets */}
                <div className="pos-quick-amounts">
                  <button
                    type="button"
                    className="pos-quick-btn"
                    onClick={() => setAmountTendered(String(totalAmount))}
                  >
                    Exact ({formatPrice(totalAmount)})
                  </button>
                  {[500, 1000, 2000].map((preset) => {
                    if (preset >= totalAmount) {
                      return (
                        <button
                          key={preset}
                          type="button"
                          className="pos-quick-btn"
                          onClick={() => setAmountTendered(String(preset))}
                        >
                          Rs. {preset}
                        </button>
                      );
                    }
                    return null;
                  })}
                </div>

                {/* Live Change Due Display */}
                {Number(amountTendered) >= totalAmount && (
                  <div className="pos-change-box">
                    <span>Change Due to Customer</span>
                    <span>{formatPrice(changeDue)}</span>
                  </div>
                )}
              </div>
            )}

            {/* Electronic / Credit Reference */}
            {paymentMethod !== "CASH" && (
              <div className="pos-form-section">
                <label htmlFor="payment-ref-input" className="pos-form-label">
                  Payment Reference / Auth Code
                </label>
                <input
                  id="payment-ref-input"
                  type="text"
                  className="pos-input-text"
                  placeholder={
                    paymentMethod === "QR_PAYMENT"
                      ? "LankaQR Reference / Transaction ID (optional)"
                      : paymentMethod === "CARD"
                      ? "Card Slip Auth Code / Batch #"
                      : paymentMethod === "CREDIT"
                      ? "Purchase Order / Authorization #"
                      : "Bank Transfer Reference ID"
                  }
                  value={paymentReference}
                  onChange={(e) => setPaymentReference(e.target.value)}
                />
              </div>
            )}

            {/* Cart Summary inside Modal */}
            <div className="pos-checkout-summary-box">
              <div className="pos-summary-row">
                <span>Items Count:</span>
                <span style={{ fontWeight: "600" }}>{totalItemsCount} units</span>
              </div>
              <div className="pos-summary-row">
                <span>Subtotal:</span>
                <span>{formatPrice(subtotal)}</span>
              </div>
              {discount > 0 && (
                <div className="pos-summary-row" style={{ color: "#16a34a" }}>
                  <span>Discount:</span>
                  <span>-{formatPrice(discount)}</span>
                </div>
              )}
              <div className="pos-summary-total-row">
                <span>Total Payable:</span>
                <span style={{ color: "var(--accent)" }}>{formatPrice(totalAmount)}</span>
              </div>
            </div>

            <div className="pos-modal-footer">
              <button type="button" className="pos-btn-secondary" onClick={onClose}>
                Cancel
              </button>
              <button type="submit" className="pos-btn-primary">
                Review & Confirm
              </button>
            </div>
          </form>
        )}

        {/* STEP 2: CONFIRMATION REVIEW */}
        {step === "confirm" && (
          <div className="pos-modal-body">
            {/* Error Banner on Failure (Requirements 5, 6, 9) */}
            {submissionError && (
              <div className="pos-alert-error" role="alert">
                <span className="pos-alert-error-title">
                  ⚠️ {submissionError.title}
                </span>
                <span className="pos-alert-error-msg">
                  {submissionError.message}
                </span>
              </div>
            )}

            <p style={{ color: "var(--text)", fontSize: "14px", margin: "0 0 12px 0" }}>
              Please verify the sale details before confirming transaction:
            </p>

            <div className="pos-checkout-summary-box">
              <div className="pos-summary-row">
                <span>Customer:</span>
                <span style={{ fontWeight: "600", color: "var(--text-h)" }}>
                  {isWalkIn ? "Walk-in Customer" : `Customer ID #${customerId}`}
                </span>
              </div>
              <div className="pos-summary-row">
                <span>Payment Method:</span>
                <span style={{ fontWeight: "600", color: "var(--text-h)" }}>
                  {formatPaymentMethod(paymentMethod)}
                </span>
              </div>
              {paymentMethod === "CASH" && (
                <>
                  <div className="pos-summary-row">
                    <span>Amount Tendered:</span>
                    <span>{formatPrice(amountTendered)}</span>
                  </div>
                  <div className="pos-summary-row" style={{ color: "#15803d", fontWeight: "600" }}>
                    <span>Change Due:</span>
                    <span>{formatPrice(changeDue)}</span>
                  </div>
                </>
              )}
              {paymentReference && (
                <div className="pos-summary-row">
                  <span>Reference:</span>
                  <span>{paymentReference}</span>
                </div>
              )}
              <div className="pos-summary-row">
                <span>Total Items:</span>
                <span>{totalItemsCount}</span>
              </div>
              <div className="pos-summary-row">
                <span>Subtotal:</span>
                <span>{formatPrice(subtotal)}</span>
              </div>
              {discount > 0 && (
                <div className="pos-summary-row" style={{ color: "#16a34a" }}>
                  <span>Discount:</span>
                  <span>-{formatPrice(discount)}</span>
                </div>
              )}
              <div className="pos-summary-total-row">
                <span>Final Total:</span>
                <span style={{ color: "var(--accent)" }}>{formatPrice(totalAmount)}</span>
              </div>
            </div>

            <div className="pos-modal-footer">
              <button
                type="button"
                className="pos-btn-secondary"
                onClick={() => setStep("form")}
                disabled={isSubmitting}
              >
                Back to Edit
              </button>
              <button
                type="button"
                className="pos-btn-primary"
                onClick={handleFinalConfirm}
                disabled={isSubmitting}
              >
                {isSubmitting ? (
                  <>
                    <span className="pos-spinner" />
                    Processing Transaction...
                  </>
                ) : (
                  "Confirm & Commit Sale"
                )}
              </button>
            </div>
          </div>
        )}

        {/* STEP 3: TRANSACTION SUCCESS & INVOICE RECEIPT */}
        {step === "success" && (
          <div className="pos-modal-body">
            <div className="pos-confirm-success">
              <h3 style={{ margin: "0", color: "#15803d" }}>
                ✓ Sale Completed Successfully!
              </h3>
              <p style={{ margin: "0", fontSize: "14px", color: "var(--text-h)" }}>
                Invoice <strong>{completedSale?.saleNumber}</strong> has been finalized and recorded.
              </p>
            </div>

            {completedSale && (
              <div className="pos-receipt-card">
                <div className="pos-receipt-header">
                  <div>
                    <span className="pos-receipt-badge">{completedSale.status}</span>
                    <div className="pos-receipt-number" style={{ marginTop: "4px" }}>
                      {completedSale.saleNumber}
                    </div>
                  </div>
                  <div style={{ textAlign: "right", fontSize: "12px", color: "var(--text)" }}>
                    {completedSale.saleDate ? new Date(completedSale.saleDate).toLocaleString() : ""}
                  </div>
                </div>

                <div className="pos-receipt-meta">
                  <div className="pos-receipt-meta-item">
                    <span>Customer:</span>
                    <span>{completedSale.customerName}</span>
                  </div>
                  <div className="pos-receipt-meta-item">
                    <span>Cashier:</span>
                    <span>{completedSale.createdByName || "Cashier"}</span>
                  </div>
                  <div className="pos-receipt-meta-item">
                    <span>Payment:</span>
                    <span>{formatPaymentMethod(completedSale.paymentMethod)}</span>
                  </div>
                  {completedSale.paymentReference && (
                    <div className="pos-receipt-meta-item">
                      <span>Ref / Auth:</span>
                      <span>{completedSale.paymentReference}</span>
                    </div>
                  )}
                  {completedSale.amountTendered != null && (
                    <div className="pos-receipt-meta-item">
                      <span>Tendered:</span>
                      <span>{formatPrice(completedSale.amountTendered)}</span>
                    </div>
                  )}
                  {completedSale.changeDue != null && (
                    <div className="pos-receipt-meta-item">
                      <span>Change Due:</span>
                      <span>{formatPrice(completedSale.changeDue)}</span>
                    </div>
                  )}
                </div>

                {/* Purchased Items Table */}
                {completedSale.items && completedSale.items.length > 0 && (
                  <table className="pos-receipt-table">
                    <thead>
                      <tr>
                        <th>Part #</th>
                        <th>Item</th>
                        <th className="text-right">Qty</th>
                        <th className="text-right">Price</th>
                        <th className="text-right">Total</th>
                      </tr>
                    </thead>
                    <tbody>
                      {completedSale.items.map((item, idx) => (
                        <tr key={item.id || idx}>
                          <td>{item.partNumber || `P-${item.productId}`}</td>
                          <td>{item.productName || `Product #${item.productId}`}</td>
                          <td className="text-right">{item.quantity}</td>
                          <td className="text-right">{formatPrice(item.unitSellingPrice)}</td>
                          <td className="text-right">{formatPrice(item.lineTotal)}</td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                )}

                {/* Financial Summary */}
                <div style={{ borderTop: "1px dashed var(--border)", paddingTop: "8px" }}>
                  <div className="pos-summary-row">
                    <span>Subtotal:</span>
                    <span>{formatPrice(completedSale.subtotal)}</span>
                  </div>
                  {Number(completedSale.discount) > 0 && (
                    <div className="pos-summary-row" style={{ color: "#16a34a" }}>
                      <span>Discount:</span>
                      <span>-{formatPrice(completedSale.discount)}</span>
                    </div>
                  )}
                  <div className="pos-summary-total-row" style={{ marginTop: "4px" }}>
                    <span>Total Paid:</span>
                    <span style={{ color: "var(--accent)" }}>{formatPrice(completedSale.total)}</span>
                  </div>
                </div>
              </div>
            )}

            <div className="pos-modal-footer">
              <button
                type="button"
                className="pos-btn-primary"
                onClick={handleFinish}
              >
                Done / Start New Sale
              </button>
            </div>
          </div>
        )}
      </div>
    </div>
  );
}

function POS() {
  const {
    cart,
    discount,
    setDiscount,
    addToCart,
    removeFromCart,
    updateQuantity,
    increaseQuantity,
    decreaseQuantity,
    clearCart,
    subtotal,
    totalItemsCount,
    appliedDiscount,
    totalAmount,
    isDiscountExceeded,
  } = useCart([]);

  const [discountInput, setDiscountInput] = useState("");
  const [discountError, setDiscountError] = useState(null);
  const [isCheckoutOpen, setIsCheckoutOpen] = useState(false);
  const [lastSaleNotice, setLastSaleNotice] = useState(null);

  const [prevDiscount, setPrevDiscount] = useState(discount);
  if (discount !== prevDiscount) {
    setPrevDiscount(discount);
    if (discount === 0 && discountInput !== "") {
      setDiscountInput("");
      setDiscountError(null);
    }
  }

  const [searchQuery, setSearchQuery] = useState("");
  const [searchResults, setSearchResults] = useState([]);
  const [isLoading, setIsLoading] = useState(false);
  const [searchError, setSearchError] = useState(null);

  const debouncedQuery = useDebounce(searchQuery, 300);

  // Search products when debounced query changes
  useEffect(() => {
    const fetchProducts = async () => {
      const trimmed = debouncedQuery.trim();
      if (!trimmed) {
        setSearchResults([]);
        setIsLoading(false);
        setSearchError(null);
        return;
      }

      setIsLoading(true);
      setSearchError(null);

      try {
        const data = await searchProducts(trimmed);
        setSearchResults(Array.isArray(data) ? data : []);
      } catch (err) {
        setSearchError(
          err.response?.data?.message ||
            "Unable to search products from server. Please verify product service is available."
        );
        setSearchResults([]);
      } finally {
        setIsLoading(false);
      }
    };

    fetchProducts();
  }, [debouncedQuery]);

  const handleDiscountChange = (e) => {
    const val = e.target.value;
    setDiscountInput(val);

    if (val === "" || val === null) {
      setDiscount(0);
      setDiscountError(null);
      return;
    }

    const num = Number(val);
    if (isNaN(num)) {
      setDiscountError("Invalid discount amount");
      return;
    }

    if (num < 0) {
      setDiscountError("Discount cannot be negative");
      return;
    }

    // Check decimal format (max 2 decimal places)
    if (!/^\d+(\.\d{1,2})?$/.test(val.trim())) {
      setDiscountError("Max 2 decimal places allowed");
      return;
    }

    if (num > subtotal) {
      setDiscountError(`Cannot exceed subtotal (${formatPrice(subtotal)})`);
      setDiscount(num);
      return;
    }

    setDiscountError(null);
    setDiscount(num);
  };

  return (
    <div className="pos-container">
      <div className="pos-header">
        <h1>Point of Sale</h1>
        <p>Search products by name or SKU, manage the shopping cart, and view totals.</p>
      </div>

      {lastSaleNotice && (
        <div className="pos-top-toast" role="status">
          <span>
            ✓ Invoice <strong>{lastSaleNotice.saleNumber}</strong> completed successfully ({formatPrice(lastSaleNotice.total)}). Cart ready for next customer.
          </span>
          <button
            type="button"
            className="pos-top-toast-close"
            onClick={() => setLastSaleNotice(null)}
            aria-label="Dismiss message"
          >
            &times;
          </button>
        </div>
      )}

      <div className="pos-layout">
        {/* Left Panel: Product Search & Results */}
        <section className="pos-search-panel" aria-label="Product Search Section">
          <div className="pos-search-input-wrapper">
            <input
              type="text"
              className="pos-search-input"
              value={searchQuery}
              onChange={(e) => setSearchQuery(e.target.value)}
              placeholder="Search products by name, SKU, or part number..."
              aria-label="Search products"
            />
          </div>

          {/* Search Status & Results */}
          {isLoading && (
            <div className="pos-status-box" role="status">
              <p>Searching products...</p>
            </div>
          )}

          {searchError && (
            <div className="pos-error-box" role="alert">
              <p>{searchError}</p>
            </div>
          )}

          {!isLoading && !searchError && searchQuery.trim() && searchResults.length === 0 && (
            <div className="pos-status-box">
              <p>No products found matching &quot;{searchQuery.trim()}&quot;.</p>
            </div>
          )}

          {!isLoading && !searchError && !searchQuery.trim() && (
            <div className="pos-status-box">
              <p>Type in the search bar above to look up products by name or SKU.</p>
            </div>
          )}

          {!isLoading && searchResults.length > 0 && (
            <div className="pos-products-grid">
              {searchResults.map((product) => {
                const id = product.productId || product.id;
                const sku = product.sku || product.partNumber || "N/A";
                const price = Number(
                  product.sellingPrice || product.unitSellingPrice || product.price || 0
                );

                return (
                  <div key={id} className="pos-product-card">
                    <div>
                      <h3 className="pos-product-name">{product.name}</h3>
                      <div className="pos-product-sku">SKU: {sku}</div>
                      {product.categoryName && (
                        <div style={{ fontSize: "12px", color: "var(--text)", marginTop: "4px" }}>
                          {product.categoryName}
                        </div>
                      )}
                    </div>
                    <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center" }}>
                      <div>
                        <div className="pos-product-price">{formatPrice(price)}</div>
                        {product.availableQuantity !== undefined && (
                          <div className="pos-product-stock">Stock: {product.availableQuantity}</div>
                        )}
                      </div>
                      <button
                        type="button"
                        className="pos-btn-add"
                        onClick={() => addToCart(product)}
                      >
                        + Add to Cart
                      </button>
                    </div>
                  </div>
                );
              })}
            </div>
          )}
        </section>

        {/* Right Panel: Shopping Cart Section */}
        <section className="pos-cart-panel" aria-label="Shopping Cart Section">
          <div className="pos-cart-header">
            <h2 className="pos-cart-title">
              Shopping Cart
              {totalItemsCount > 0 && (
                <span className="pos-cart-badge">{totalItemsCount} {totalItemsCount === 1 ? "item" : "items"}</span>
              )}
            </h2>
            {cart.length > 0 && (
              <button
                type="button"
                className="pos-btn-clear"
                onClick={clearCart}
                title="Clear all cart items"
              >
                Clear Cart
              </button>
            )}
          </div>

          {cart.length === 0 ? (
            <div className="pos-cart-empty">
              <p>No items in cart.</p>
              <p style={{ fontSize: "13px", marginTop: "4px" }}>
                Search for products on the left and click &quot;+ Add to Cart&quot; to begin.
              </p>
            </div>
          ) : (
            <div className="pos-cart-items-list">
              {cart.map((item) => (
                <CartItemRow
                  key={item.id}
                  item={item}
                  onUpdateQuantity={updateQuantity}
                  onIncrease={increaseQuantity}
                  onDecrease={decreaseQuantity}
                  onRemove={removeFromCart}
                  formatPrice={formatPrice}
                />
              ))}
            </div>
          )}

          {/* Cart Summary Area */}
          <div className="pos-cart-summary">
            <div className="pos-summary-row">
              <span>Total Items</span>
              <span style={{ fontWeight: "500", color: "var(--text-h)" }}>{totalItemsCount}</span>
            </div>

            <div className="pos-summary-row">
              <span>Subtotal</span>
              <span style={{ fontWeight: "500", color: "var(--text-h)" }}>{formatPrice(subtotal)}</span>
            </div>

            <div className="pos-summary-discount-row">
              <label htmlFor="pos-discount-input">Discount</label>
              <div style={{ display: "flex", flexDirection: "column", alignItems: "flex-end" }}>
                <div style={{ display: "flex", alignItems: "center", gap: "4px" }}>
                  <span style={{ fontSize: "13px", color: "var(--text-muted)" }}>Rs.</span>
                  <input
                    id="pos-discount-input"
                    type="number"
                    min="0"
                    max={subtotal}
                    step="0.01"
                    className={`pos-discount-input ${isDiscountExceeded || discountError ? "pos-input-error" : ""}`}
                    value={discountInput}
                    onChange={handleDiscountChange}
                    placeholder="0.00"
                    aria-label="Discount amount in Sri Lankan Rupees"
                  />
                </div>
                {(discountError || (isDiscountExceeded && !discountError)) && (
                  <span className="pos-discount-error-text">
                    {discountError || `Exceeds subtotal (${formatPrice(subtotal)})`}
                  </span>
                )}
              </div>
            </div>

            {appliedDiscount > 0 && (
              <div className="pos-summary-row" style={{ color: "#16a34a", fontSize: "13px" }}>
                <span>Applied Discount</span>
                <span>-{formatPrice(appliedDiscount)}</span>
              </div>
            )}

            <div className="pos-summary-total-row">
              <span>Total Payable</span>
              <span>{formatPrice(totalAmount)}</span>
            </div>

            <button
              type="button"
              disabled={cart.length === 0 || isDiscountExceeded || Boolean(discountError)}
              className="pos-checkout-btn"
              onClick={() => setIsCheckoutOpen(true)}
            >
              Proceed to Checkout
            </button>
            <p className="pos-checkout-note">
              Server recalculates and enforces total = subtotal - discount authoritatively on checkout.
            </p>
          </div>
        </section>
      </div>

      {/* Checkout Modal */}
      <CheckoutModal
        isOpen={isCheckoutOpen}
        onClose={() => setIsCheckoutOpen(false)}
        cart={cart}
        subtotal={subtotal}
        discount={appliedDiscount}
        totalAmount={totalAmount}
        totalItemsCount={totalItemsCount}
        formatPrice={formatPrice}
        onClearCart={clearCart}
        onCheckoutSuccess={(sale) => setLastSaleNotice(sale)}
      />
    </div>
  );
}

export default POS;
