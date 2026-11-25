import { describe, it } from "node:test";
import assert from "node:assert/strict";

describe("Sales Invoice & Receipt Data Normalization", () => {
  // Pure normalizer function matching Invoice.jsx implementation
  function normalizeReceiptData(receipt) {
    return {
      storeName: receipt.storeInfo?.storeName || "Bajaj Genuine Spare Parts - Central Depot",
      storeAddress: receipt.storeInfo?.address || "45 Commercial Road, Automotive Hub",
      storePhone: receipt.storeInfo?.phone || "+91 80 2345 6789",
      storeGstin: receipt.storeInfo?.gstin || "29AAAAA0000A1Z5",
      saleNumber: receipt.receiptInfo?.saleNumber || "INV-UNKNOWN",
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
    const taxIncluded = (total * 18) / 118;

    return {
      storeName: "Bajaj Genuine Spare Parts - Central Depot",
      saleNumber: sale.saleNumber || "INV-UNKNOWN",
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
    };
  }

  const sampleReceiptResponse = {
    storeInfo: {
      storeName: "Bajaj Genuine Spare Parts - Central Depot",
      address: "45 Commercial Road, Automotive Hub",
      phone: "+91 80 2345 6789",
      gstin: "29AAAAA0000A1Z5",
    },
    receiptInfo: {
      saleNumber: "INV-202611-0001",
      saleDate: "2026-11-13T14:30:00Z",
      cashierName: "Sunil Sharma",
      customerName: "Walk-in Customer",
      customerPhone: null,
    },
    items: [
      {
        partNumber: "DH101048",
        description: "Clutch Plate Assembly",
        quantity: 2,
        unitPrice: 220.0,
        lineTotal: 440.0,
      },
    ],
    totals: {
      subtotal: 440.0,
      discount: 0.0,
      taxIncluded: 67.12,
      grandTotal: 440.0,
    },
    payment: {
      paymentMethod: "CASH",
      amountTendered: 500.0,
      changeDue: 60.0,
      reference: null,
    },
    footerNote: "Thank you for buying Bajaj Genuine Parts! Goods once sold can be exchanged within 7 days in original condition.",
  };

  describe("Receipt Response Contract Processing", () => {
    it("should correctly extract invoice number, date, customer, and cashier (Requirements 1, 2, 3)", () => {
      const data = normalizeReceiptData(sampleReceiptResponse);

      assert.equal(data.saleNumber, "INV-202611-0001");
      assert.equal(data.saleDate, "2026-11-13T14:30:00Z");
      assert.equal(data.customerName, "Walk-in Customer");
      assert.equal(data.cashierName, "Sunil Sharma");
      assert.equal(data.status, "COMPLETED");
    });

    it("should correctly extract purchased items, quantities, and price snapshots (Requirements 4, 5, 10)", () => {
      const data = normalizeReceiptData(sampleReceiptResponse);

      assert.equal(data.items.length, 1);
      assert.equal(data.items[0].partNumber, "DH101048");
      assert.equal(data.items[0].name, "Clutch Plate Assembly");
      assert.equal(data.items[0].quantity, 2);
      assert.equal(data.items[0].unitPrice, 220.0);
      assert.equal(data.items[0].lineTotal, 440.0);
    });

    it("should correctly extract financial totals and payment details (Requirements 6, 7, 8, 9)", () => {
      const data = normalizeReceiptData(sampleReceiptResponse);

      assert.equal(data.subtotal, 440.0);
      assert.equal(data.discount, 0.0);
      assert.equal(data.grandTotal, 440.0);
      assert.equal(data.paymentMethod, "CASH");
      assert.equal(data.amountTendered, 500.0);
      assert.equal(data.changeDue, 60.0);
    });
  });

  describe("Standard Sale Response Fallback Processing", () => {
    const sampleSaleResponse = {
      id: 1042,
      saleNumber: "INV-202611-0042",
      customerId: 42,
      customerName: "Metro Auto Repairs",
      customerPhone: "+91 9876543210",
      saleDate: "2026-11-14T10:15:00Z",
      status: "COMPLETED",
      subtotal: 870.0,
      discount: 50.0,
      total: 820.0,
      paymentMethod: "CREDIT",
      paymentReference: "PO-9921",
      createdByName: "Sunil Sharma",
      items: [
        {
          id: 5001,
          productId: 101,
          partNumber: "DH101048",
          productName: "Clutch Plate Assembly",
          quantity: 2,
          unitSellingPrice: 220.0,
          unitCostSnapshot: 160.0,
          lineTotal: 440.0,
        },
        {
          id: 5002,
          productId: 205,
          partNumber: "36JZ0001",
          productName: "Bajaj DTS-i 4T Oil",
          quantity: 1,
          unitSellingPrice: 430.0,
          unitCostSnapshot: 310.0,
          lineTotal: 430.0,
        },
      ],
    };

    it("should normalize registered customer credit sale with customer details and line snapshots", () => {
      const data = normalizeSaleData(sampleSaleResponse);

      assert.equal(data.saleNumber, "INV-202611-0042");
      assert.equal(data.customerName, "Metro Auto Repairs");
      assert.equal(data.customerId, 42);
      assert.equal(data.customerPhone, "+91 9876543210");
      assert.equal(data.paymentMethod, "CREDIT");
      assert.equal(data.paymentReference, "PO-9921");
      assert.equal(data.subtotal, 870.0);
      assert.equal(data.discount, 50.0);
      assert.equal(data.grandTotal, 820.0);
      assert.equal(data.items.length, 2);
      assert.equal(data.items[0].unitCostSnapshot, 160.0);
      assert.equal(data.items[1].unitCostSnapshot, 310.0);
    });

    it("should handle walk-in sale when customerId is null", () => {
      const walkInSale = {
        ...sampleSaleResponse,
        customerId: null,
        customerName: null,
      };

      const data = normalizeSaleData(walkInSale);
      assert.equal(data.customerName, "Walk-in Customer");
      assert.equal(data.customerId, null);
    });
  });
});

