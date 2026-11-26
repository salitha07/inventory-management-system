import { useEffect, useRef, useState } from "react";
import purchaseService from "../../services/purchaseService";
import purchaseLookupService from "../../services/purchaseLookupService";

import {
  calculateLineTotal,
  calculatePurchaseTotal,
  validatePurchaseForm,
} from "../../utils/purchaseOperations";

import { formatPrice } from "../../utils/currency";

function getLocalDate() {
  const date = new Date();

  const year = date.getFullYear();
  const month = String(date.getMonth() + 1).padStart(2, "0");
  const day = String(date.getDate()).padStart(2, "0");

  return `${year}-${month}-${day}`;
}

function createEmptyItem(rowId) {
  return {
    rowId,
    productId: "",
    quantity: "1",
    unitCost: "",
  };
}

function createInitialForm() {
  return {
    supplierId: "",
    supplierInvoiceNumber: "",
    purchaseDate: getLocalDate(),
    discount: "0",
    items: [createEmptyItem(1)],
  };
}

function getErrorMessage(error, fallback) {
  return error.response?.data?.message || error.message || fallback;
}

function FieldError({ message }) {
  if (!message) return null;

  return (
    <span className="purchase-field-error" role="alert">
      {message}
    </span>
  );
}

export default function PurchaseForm({ onSuccess, onCancel }) {
  const [form, setForm] = useState(createInitialForm);
  const [suppliers, setSuppliers] = useState([]);
  const [products, setProducts] = useState([]);
  const [selectedProducts, setSelectedProducts] = useState({});

  const [productSearch, setProductSearch] = useState("");
  const [productPage, setProductPage] = useState(0);
  const [totalPages, setTotalPages] = useState(0);

  const [loadingSuppliers, setLoadingSuppliers] = useState(true);
  const [loadingProducts, setLoadingProducts] = useState(true);
  const [submitting, setSubmitting] = useState(false);

  const [supplierError, setSupplierError] = useState("");
  const [productError, setProductError] = useState("");
  const [submitError, setSubmitError] = useState("");
  const [errors, setErrors] = useState({});
  const [retryCount, setRetryCount] = useState(0);

  const nextRowId = useRef(2);
  const submitLock = useRef(false);

  const totals = calculatePurchaseTotal(
    form.items,
    form.discount
  );

  useEffect(() => {
    let cancelled = false;

    async function loadSuppliers() {
      try {
        const result =
          await purchaseLookupService.getActiveSuppliers();

        if (!cancelled) {
          setSuppliers(result);
          setSupplierError("");
        }
      } catch (error) {
        if (!cancelled) {
          setSupplierError(
            getErrorMessage(error, "Unable to load suppliers.")
          );
        }
      } finally {
        if (!cancelled) {
          setLoadingSuppliers(false);
        }
      }
    }

    loadSuppliers();

    return () => {
      cancelled = true;
    };
  }, [retryCount]);

  useEffect(() => {
    let cancelled = false;

    const timer = setTimeout(async () => {
      try {
        const result =
          await purchaseLookupService.getActiveProducts({
            search: productSearch,
            page: productPage,
            size: 20,
          });

        if (!cancelled) {
          setProducts(result.products);
          setTotalPages(result.totalPages);
          setProductError("");
        }
      } catch (error) {
        if (!cancelled) {
          setProducts([]);
          setTotalPages(0);

          setProductError(
            getErrorMessage(error, "Unable to load products.")
          );
        }
      } finally {
        if (!cancelled) {
          setLoadingProducts(false);
        }
      }
    }, 300);

    return () => {
      cancelled = true;
      clearTimeout(timer);
    };
  }, [productSearch, productPage, retryCount]);

  function updateField(field, value) {
    setForm((previous) => ({
      ...previous,
      [field]: value,
    }));

    setErrors({});
    setSubmitError("");
  }

  function updateItem(rowId, field, value) {
    setForm((previous) => ({
      ...previous,
      items: previous.items.map((item) =>
        item.rowId === rowId
          ? { ...item, [field]: value }
          : item
      ),
    }));

    setErrors({});
    setSubmitError("");
  }

  function selectProduct(rowId, productId) {
    const product =
      products.find(
        (entry) => String(entry.id) === productId
      ) || selectedProducts[productId];

    if (product) {
      setSelectedProducts((previous) => ({
        ...previous,
        [productId]: product,
      }));
    }

    setForm((previous) => ({
      ...previous,
      items: previous.items.map((item) =>
        item.rowId === rowId
          ? {
              ...item,
              productId,
              unitCost: String(product?.costPrice ?? ""),
            }
          : item
      ),
    }));

    setErrors({});
    setSubmitError("");
  }

  function addItem() {
    const rowId = nextRowId.current++;

    setForm((previous) => ({
      ...previous,
      items: [...previous.items, createEmptyItem(rowId)],
    }));

    setErrors({});
  }

  function removeItem(rowId) {
    setForm((previous) => ({
      ...previous,
      items: previous.items.filter(
        (item) => item.rowId !== rowId
      ),
    }));

    setErrors({});
  }

  function handleProductSearch(value) {
    if (value === productSearch && productPage === 0) {
      return;
    }

    setLoadingProducts(true);
    setProductSearch(value);
    setProductPage(0);
  }

  function changeProductPage(page) {
    if (page === productPage) return;

    setLoadingProducts(true);
    setProductPage(page);
  }

  function retryLookups() {
    setLoadingSuppliers(true);
    setLoadingProducts(true);
    setRetryCount((previous) => previous + 1);
  }

  function getProductOptions(item) {
    const selected = selectedProducts[item.productId];

    if (
      selected &&
      !products.some(
        (product) => String(product.id) === item.productId
      )
    ) {
      return [selected, ...products];
    }

    return products;
  }

  async function handleSubmit(event) {
    event.preventDefault();

    if (submitLock.current) return;

    setSubmitError("");

    const validation = validatePurchaseForm(form);

    setErrors(validation.errors);

    if (!validation.isValid) {
      return;
    }

    submitLock.current = true;
    setSubmitting(true);

    let createdPurchase;

    try {
      createdPurchase = await purchaseService.createPurchase(
        validation.payload
      );
    } catch (error) {
      setSubmitError(
        getErrorMessage(error, "Unable to create purchase.")
      );

      return;
    } finally {
      submitLock.current = false;
      setSubmitting(false);
    }

    setForm(createInitialForm());
    setSelectedProducts({});
    setErrors({});

    onSuccess?.(createdPurchase);
  }

  return (
    <form
      className="purchase-form"
      onSubmit={handleSubmit}
      noValidate
    >
      <div className="purchase-form-header">
        <h2>Create Purchase</h2>

        <p>
          Record a supplier invoice and create a draft purchase.
        </p>
      </div>

      {submitError && (
        <div className="purchase-alert-error" role="alert">
          {submitError}
        </div>
      )}

      {(supplierError || productError) && (
        <div className="purchase-alert-error" role="alert">
          {supplierError && <p>{supplierError}</p>}
          {productError && <p>{productError}</p>}

          <button
            type="button"
            onClick={retryLookups}
            disabled={loadingSuppliers || loadingProducts}
          >
            Retry
          </button>
        </div>
      )}

      <fieldset disabled={submitting}>
        <div className="purchase-form-grid">
          <div className="purchase-field">
            <label htmlFor="purchase-supplier">
              Supplier
            </label>

            <select
              id="purchase-supplier"
              value={form.supplierId}
              onChange={(event) =>
                updateField("supplierId", event.target.value)
              }
              disabled={loadingSuppliers}
            >
              <option value="">
                {loadingSuppliers
                  ? "Loading suppliers..."
                  : "Select supplier"}
              </option>

              {suppliers.map((supplier) => (
                <option
                  key={supplier.id}
                  value={supplier.id}
                >
                  {supplier.supplierName}
                </option>
              ))}
            </select>

            <FieldError message={errors.supplierId} />
          </div>

          <div className="purchase-field">
            <label htmlFor="purchase-invoice">
              Supplier Invoice Number
            </label>

            <input
              id="purchase-invoice"
              type="text"
              maxLength={100}
              value={form.supplierInvoiceNumber}
              onChange={(event) =>
                updateField(
                  "supplierInvoiceNumber",
                  event.target.value
                )
              }
              placeholder="Enter invoice number"
            />

            <FieldError
              message={errors.supplierInvoiceNumber}
            />
          </div>

          <div className="purchase-field">
            <label htmlFor="purchase-date">
              Purchase Date
            </label>

            <input
              id="purchase-date"
              type="date"
              value={form.purchaseDate}
              onChange={(event) =>
                updateField("purchaseDate", event.target.value)
              }
            />

            <FieldError message={errors.purchaseDate} />
          </div>
        </div>

        <section className="purchase-items-section">
          <h3>Purchase Items</h3>

          <div className="purchase-product-search">
            <label htmlFor="purchase-product-search">
              Search Products
            </label>

            <input
              id="purchase-product-search"
              type="search"
              value={productSearch}
              onChange={(event) =>
                handleProductSearch(event.target.value)
              }
              placeholder="Search by product name or code"
            />

            {loadingProducts && (
              <p>Loading products...</p>
            )}

            {!loadingProducts &&
              !productError &&
              products.length === 0 && (
                <p>No active products found.</p>
              )}

            {totalPages > 1 && (
              <div className="purchase-pagination">
                <button
                  type="button"
                  disabled={
                    productPage === 0 || loadingProducts
                  }
                  onClick={() =>
                    changeProductPage(productPage - 1)
                  }
                >
                  Previous
                </button>

                <span>
                  Page {productPage + 1} of {totalPages}
                </span>

                <button
                  type="button"
                  disabled={
                    productPage + 1 >= totalPages ||
                    loadingProducts
                  }
                  onClick={() =>
                    changeProductPage(productPage + 1)
                  }
                >
                  Next
                </button>
              </div>
            )}
          </div>

          <div className="purchase-table-wrapper">
            <table className="purchase-items-table">
              <thead>
                <tr>
                  <th>Product</th>
                  <th>Quantity</th>
                  <th>Unit Cost</th>
                  <th>Line Total</th>
                  <th>Action</th>
                </tr>
              </thead>

              <tbody>
                {form.items.map((item, index) => {
                  const itemErrors =
                    errors.itemErrors?.[index] || {};

                  return (
                    <tr key={item.rowId}>
                      <td>
                        <select
                          aria-label={`Product ${index + 1}`}
                          value={item.productId}
                          disabled={loadingProducts}
                          onChange={(event) =>
                            selectProduct(
                              item.rowId,
                              event.target.value
                            )
                          }
                        >
                          <option value="">
                            Select product
                          </option>

                          {getProductOptions(item).map(
                            (product) => (
                              <option
                                key={product.id}
                                value={product.id}
                              >
                                {product.productCode} -{" "}
                                {product.productName}
                              </option>
                            )
                          )}
                        </select>

                        <FieldError
                          message={itemErrors.productId}
                        />
                      </td>

                      <td>
                        <input
                          aria-label={`Quantity ${index + 1}`}
                          type="number"
                          min="1"
                          step="1"
                          value={item.quantity}
                          onChange={(event) =>
                            updateItem(
                              item.rowId,
                              "quantity",
                              event.target.value
                            )
                          }
                        />

                        <FieldError
                          message={itemErrors.quantity}
                        />
                      </td>

                      <td>
                        <input
                          aria-label={`Unit cost ${index + 1}`}
                          type="number"
                          min="0"
                          step="0.01"
                          value={item.unitCost}
                          onChange={(event) =>
                            updateItem(
                              item.rowId,
                              "unitCost",
                              event.target.value
                            )
                          }
                        />

                        <FieldError
                          message={itemErrors.unitCost}
                        />
                      </td>

                      <td>
                        {formatPrice(
                          calculateLineTotal(
                            item.quantity,
                            item.unitCost
                          )
                        )}

                        <FieldError
                          message={itemErrors.lineTotal}
                        />
                      </td>

                      <td>
                        <button
                          type="button"
                          className="purchase-remove-button"
                          onClick={() =>
                            removeItem(item.rowId)
                          }
                          disabled={form.items.length === 1}
                        >
                          Remove
                        </button>
                      </td>
                    </tr>
                  );
                })}
              </tbody>
            </table>
          </div>

          <FieldError message={errors.items} />

          <button
            type="button"
            className="purchase-add-button"
            onClick={addItem}
          >
            + Add Item
          </button>
        </section>

        <section className="purchase-totals">
          <div className="purchase-total-row">
            <span>Subtotal</span>
            <strong>{formatPrice(totals.subtotal)}</strong>
          </div>

          <FieldError message={errors.subtotal} />

          <div className="purchase-field">
            <label htmlFor="purchase-discount">
              Discount
            </label>

            <input
              id="purchase-discount"
              type="number"
              min="0"
              step="0.01"
              value={form.discount}
              onChange={(event) =>
                updateField("discount", event.target.value)
              }
            />

            <FieldError message={errors.discount} />
          </div>

          <div className="purchase-total-row purchase-grand-total">
            <span>Total Amount</span>
            <strong>{formatPrice(totals.total)}</strong>
          </div>
        </section>

        <div className="purchase-form-actions">
          <button
            type="button"
            className="purchase-secondary-button"
            onClick={() => onCancel?.()}
          >
            Cancel
          </button>

          <button
            type="submit"
            className="purchase-primary-button"
            disabled={
              submitting ||
              loadingSuppliers ||
              loadingProducts ||
              Boolean(supplierError) ||
              Boolean(productError)
            }
          >
            {submitting ? "Saving..." : "Create Purchase"}
          </button>
        </div>
      </fieldset>
    </form>
  );
}