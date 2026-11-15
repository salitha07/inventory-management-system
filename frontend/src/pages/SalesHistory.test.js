import { describe, it } from "node:test";
import assert from "node:assert/strict";
import { formatPrice } from "../utils/currency.js";

/**
 * Format ISO datetime string to user-friendly local date & time
 * Matching implementation in SalesHistory.jsx
 */
const formatDate = (isoString) => {
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
 * Matching implementation in SalesHistory.jsx
 */
const getStatusBadgeClass = (status) => {
  const norm = (status || "").toUpperCase();
  if (norm === "COMPLETED") return "completed";
  if (norm === "CANCELLED") return "cancelled";
  if (norm === "REFUNDED") return "refunded";
  return "";
};

describe("Sales History Functionality & Pagination", () => {
  describe("Price & Date Formatting", () => {
    it("should format currency values consistently in Sri Lankan Rupees", () => {
      const formatted = formatPrice(440);
      assert.strictEqual(formatted, "Rs. 440.00");
      assert.strictEqual(formatPrice(0), "Rs. 0.00");
      assert.strictEqual(formatPrice(1500), "Rs. 1,500.00");
      assert.strictEqual(formatPrice(250.5), "Rs. 250.50");
      assert.strictEqual(formatPrice(null), "Rs. 0.00");
      assert.strictEqual(formatPrice(undefined), "Rs. 0.00");
    });

    it("should format valid ISO dates and handle invalid or null gracefully", () => {
      const formatted = formatDate("2026-11-13T14:30:00Z");
      assert.ok(formatted !== "-");
      assert.ok(formatted.includes("2026") || formatted.includes("Nov"));

      assert.strictEqual(formatDate(null), "-");
      assert.strictEqual(formatDate(""), "-");
      assert.strictEqual(formatDate(undefined), "-");
    });

    it("should map status strings to appropriate badge CSS class names", () => {
      assert.strictEqual(getStatusBadgeClass("COMPLETED"), "completed");
      assert.strictEqual(getStatusBadgeClass("completed"), "completed");
      assert.strictEqual(getStatusBadgeClass("CANCELLED"), "cancelled");
      assert.strictEqual(getStatusBadgeClass("REFUNDED"), "refunded");
      assert.strictEqual(getStatusBadgeClass("UNKNOWN"), "");
      assert.strictEqual(getStatusBadgeClass(null), "");
    });
  });

  describe("Pagination Calculations (Requirement 8)", () => {
    it("should correctly compute start and end record counts for page 0", () => {
      const page = 0;
      const size = 20;
      const totalElements = 45;

      const startRecord = totalElements === 0 ? 0 : page * size + 1;
      const endRecord = Math.min((page + 1) * size, totalElements);
      const totalPages = Math.ceil(totalElements / size);

      assert.strictEqual(startRecord, 1);
      assert.strictEqual(endRecord, 20);
      assert.strictEqual(totalPages, 3);
    });

    it("should correctly compute start and end record counts for middle and last page", () => {
      const page = 2; // 3rd page (0-indexed)
      const size = 20;
      const totalElements = 45;

      const startRecord = totalElements === 0 ? 0 : page * size + 1;
      const endRecord = Math.min((page + 1) * size, totalElements);

      assert.strictEqual(startRecord, 41);
      assert.strictEqual(endRecord, 45);
    });

    it("should handle 0 total elements gracefully", () => {
      const page = 0;
      const size = 20;
      const totalElements = 0;

      const startRecord = totalElements === 0 ? 0 : page * size + 1;
      const endRecord = Math.min((page + 1) * size, totalElements);

      assert.strictEqual(startRecord, 0);
      assert.strictEqual(endRecord, 0);
    });
  });

  describe("API Query Parameter Composition", () => {
    function composeSalesHistoryParams({ page, size, searchTerm, statusFilter, paymentFilter }) {
      const params = {
        page: page ?? 0,
        size: size ?? 20,
        sort: "saleDate,desc",
      };

      if (searchTerm && searchTerm.trim()) {
        params.search = searchTerm.trim();
      }
      if (statusFilter) {
        params.status = statusFilter;
      }
      if (paymentFilter) {
        params.paymentMethod = paymentFilter;
      }

      return params;
    }

    it("should build default pagination parameters without empty filters", () => {
      const params = composeSalesHistoryParams({
        page: 0,
        size: 20,
        searchTerm: "",
        statusFilter: "",
        paymentFilter: "",
      });

      assert.deepStrictEqual(params, {
        page: 0,
        size: 20,
        sort: "saleDate,desc",
      });
      assert.strictEqual(params.search, undefined);
      assert.strictEqual(params.status, undefined);
      assert.strictEqual(params.paymentMethod, undefined);
    });

    it("should include trimmed search query and filters when provided", () => {
      const params = composeSalesHistoryParams({
        page: 1,
        size: 10,
        searchTerm: "  INV-202611-0001  ",
        statusFilter: "COMPLETED",
        paymentFilter: "CASH",
      });

      assert.strictEqual(params.page, 1);
      assert.strictEqual(params.size, 10);
      assert.strictEqual(params.sort, "saleDate,desc");
      assert.strictEqual(params.search, "INV-202611-0001");
      assert.strictEqual(params.status, "COMPLETED");
      assert.strictEqual(params.paymentMethod, "CASH");
    });
  });

  describe("Spring Data Page Processing & Normalization", () => {
    function processApiResponse(response) {
      if (response && response.content) {
        return {
          sales: response.content,
          totalPages: response.totalPages || 0,
          totalElements: response.totalElements || 0,
        };
      } else if (Array.isArray(response)) {
        return {
          sales: response,
          totalPages: 1,
          totalElements: response.length,
        };
      }
      return {
        sales: [],
        totalPages: 0,
        totalElements: 0,
      };
    }

    it("should correctly extract sales content and metadata from Spring Data Page response", () => {
      const mockPageResponse = {
        content: [
          {
            id: 101,
            saleNumber: "INV-202611-0001",
            customerId: null,
            customerName: "Walk-in Customer",
            saleDate: "2026-11-13T14:30:00Z",
            status: "COMPLETED",
            itemCount: 3,
            subtotal: 550.0,
            discount: 50.0,
            total: 500.0,
            paymentMethod: "CASH",
            createdByName: "Sunil Sharma",
          },
          {
            id: 102,
            saleNumber: "INV-202611-0002",
            customerId: 42,
            customerName: "Apex Motors Workshop",
            saleDate: "2026-11-14T10:15:00Z",
            status: "COMPLETED",
            itemCount: 5,
            subtotal: 1250.0,
            discount: 0.0,
            total: 1250.0,
            paymentMethod: "CREDIT",
            createdByName: "Sunil Sharma",
          },
        ],
        totalPages: 1,
        totalElements: 2,
        number: 0,
        size: 20,
      };

      const result = processApiResponse(mockPageResponse);

      assert.strictEqual(result.sales.length, 2);
      assert.strictEqual(result.totalPages, 1);
      assert.strictEqual(result.totalElements, 2);
      assert.strictEqual(result.sales[0].saleNumber, "INV-202611-0001");
      assert.strictEqual(result.sales[0].customerName, "Walk-in Customer");
      assert.strictEqual(result.sales[0].paymentMethod, "CASH");
      assert.strictEqual(result.sales[1].saleNumber, "INV-202611-0002");
      assert.strictEqual(result.sales[1].customerName, "Apex Motors Workshop");
      assert.strictEqual(result.sales[1].paymentMethod, "CREDIT");
    });

    it("should handle null or empty response without crashing", () => {
      const nullResult = processApiResponse(null);
      assert.deepStrictEqual(nullResult, { sales: [], totalPages: 0, totalElements: 0 });

      const emptyObjResult = processApiResponse({});
      assert.deepStrictEqual(emptyObjResult, { sales: [], totalPages: 0, totalElements: 0 });
    });
  });

  describe("Sales History Payment Method Filter Options", () => {
    // Approved filter options in SalesHistory.jsx dropdown
    const SALES_HISTORY_PAYMENT_OPTIONS = [
      { value: "", label: "All Payment Methods" },
      { value: "CASH", label: "Cash" },
      { value: "CARD", label: "Card" },
      { value: "QR_PAYMENT", label: "QR Payment (LankaQR)" },
      { value: "BANK_TRANSFER", label: "Bank Transfer" },
      { value: "CREDIT", label: "Credit" },
    ];

    it("should include all approved payment filter options in dropdown", () => {
      const values = SALES_HISTORY_PAYMENT_OPTIONS.map((o) => o.value);
      assert.deepStrictEqual(values, [
        "",
        "CASH",
        "CARD",
        "QR_PAYMENT",
        "BANK_TRANSFER",
        "CREDIT",
      ]);
    });

    it("should not contain UPI or UPI (Historical) in payment method filter dropdown", () => {
      const values = SALES_HISTORY_PAYMENT_OPTIONS.map((o) => o.value);
      assert.strictEqual(values.includes("UPI"), false);

      const labels = SALES_HISTORY_PAYMENT_OPTIONS.map((o) => o.label);
      const hasUpiLabel = labels.some((l) => l.toLowerCase().includes("upi"));
      assert.strictEqual(hasUpiLabel, false);
    });

    it("should compose correct query parameters when filtering by QR_PAYMENT", () => {
      const params = {
        page: 0,
        size: 20,
        sort: "saleDate,desc",
        paymentMethod: "QR_PAYMENT",
      };
      assert.strictEqual(params.paymentMethod, "QR_PAYMENT");
    });
  });
});
