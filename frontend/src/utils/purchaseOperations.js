export const MAX_PURCHASE_AMOUNT = 9999999999.99;
export const MAX_QUANTITY = 2147483647;

export const PURCHASE_STATUS = Object.freeze({
    DRAFT: "DRAFT",
    RECEIVED: "RECEIVED",
    CANCELLED: "CANCELLED",
});

export function isValidQuantity(value) {
    const quantity = Number(value);

    return (
        String(value ?? "").trim() !== "" &&
        Number.isInteger(quantity) &&
        quantity >= 1 &&
        quantity <= MAX_QUANTITY
    );
}

export function isValidAmount(value) {
    const text = String(value ?? "").trim();

    if (!/^\d+(\.\d{1,2})?$/.test(text)) {
        return false;
    }

    const amount = Number(text);

    return (
        Number.isFinite(amount) &&
        amount >= 0 &&
        amount <= MAX_PURCHASE_AMOUNT
    );
}

function toCents(value) {
    return Math.round(Number(value) * 100);
}

export function calculateLineTotal(quantity, unitCost) {
    if (
        !isValidQuantity(quantity) ||
        !isValidAmount(unitCost)
    ) {
        return 0;
    }

    return (Number(quantity) * toCents(unitCost)) / 100;
}

export function calculateSubtotal(items = []) {
    return items.reduce((total, item) => {
        return total + toCents(
            calculateLineTotal(item.quantity, item.unitCost)
        );
    }, 0) / 100;
}

export function calculatePurchaseTotal(items = [], discount = 0) {
    const subtotal = calculateSubtotal(items);

    const discountAmount = isValidAmount(discount)
        ? Number(discount)
        : 0;

    const total = (
        toCents(subtotal) - toCents(discountAmount)
    ) / 100;

    return {
        subtotal,
        discount: discountAmount,
        total: Math.max(0, total),
    };
}

function isValidId(value) {
    return (
        String(value ?? "").trim() !== "" &&
        Number.isSafeInteger(Number(value)) &&
        Number(value) > 0
    );
}

function isValidDate(value) {
    if (!/^\d{4}-\d{2}-\d{2}$/.test(value ?? "")) {
        return false;
    }

    const date = new Date(`${value}T00:00:00Z`);

    return (
        !Number.isNaN(date.getTime()) &&
        date.toISOString().slice(0, 10) === value
    );
}

export function validatePurchaseForm(form) {
    const errors = {};

    if (!isValidId(form.supplierId)) {
        errors.supplierId = "Please select a valid supplier.";
    }

    const invoiceNumber = String(
        form.supplierInvoiceNumber ?? ""
    ).trim();

    if (!invoiceNumber) {
        errors.supplierInvoiceNumber =
            "Supplier invoice number is required.";
    } else if (invoiceNumber.length > 100) {
        errors.supplierInvoiceNumber =
            "Invoice number cannot exceed 100 characters.";
    }

    if (!isValidDate(form.purchaseDate)) {
        errors.purchaseDate =
            "Please enter a valid purchase date.";
    }

    if (!isValidAmount(form.discount)) {
        errors.discount =
            "Please enter a valid discount amount.";
    }

    const items = Array.isArray(form.items)
        ? form.items
        : [];

    if (items.length === 0) {
        errors.items = "Add at least one purchase item.";
    }

    const itemErrors = {};

    items.forEach((item, index) => {
        const rowErrors = {};

        if (!isValidId(item.productId)) {
            rowErrors.productId =
                "Please select a valid product.";
        }

        if (!isValidQuantity(item.quantity)) {
            rowErrors.quantity =
                "Quantity must be a valid positive integer.";
        }

        if (!isValidAmount(item.unitCost)) {
            rowErrors.unitCost =
                "Please enter a valid unit cost.";
        }

        const lineTotal = calculateLineTotal(
            item.quantity,
            item.unitCost
        );

        if (lineTotal > MAX_PURCHASE_AMOUNT) {
            rowErrors.lineTotal =
                "Line total exceeds the allowed amount.";
        }

        if (Object.keys(rowErrors).length > 0) {
            itemErrors[index] = rowErrors;
        }
    });

    if (Object.keys(itemErrors).length > 0) {
        errors.itemErrors = itemErrors;
    }

    const totals = calculatePurchaseTotal(
        items,
        form.discount
    );

    if (totals.subtotal > MAX_PURCHASE_AMOUNT) {
        errors.subtotal =
            "Subtotal exceeds the allowed amount.";
    }

    if (totals.discount > totals.subtotal) {
        errors.discount =
            "Discount cannot exceed the subtotal.";
    }

    const isValid = Object.keys(errors).length === 0;

    return {
        isValid,
        errors,
        totals,
        payload: isValid
            ? {
                supplierId: Number(form.supplierId),
                supplierInvoiceNumber: invoiceNumber,
                purchaseDate: form.purchaseDate,
                discount: Number(form.discount),
                items: items.map((item) => ({
                    productId: Number(item.productId),
                    quantity: Number(item.quantity),
                    unitCost: Number(item.unitCost),
                })),
            }
            : null,
    };
}

export function canAccessPurchases(role) {
    return [
        "ADMIN",
        "MANAGER",
        "INVENTORY_STAFF",
    ].includes(role);
}

export function canCreatePurchase(role) {
    return canAccessPurchases(role);
}

export function canReceivePurchase(role, status) {
    return (
        ["ADMIN", "MANAGER"].includes(role) &&
        status === PURCHASE_STATUS.DRAFT
    );
}

export function canCancelPurchase(role, status) {
    return (
        ["ADMIN", "MANAGER"].includes(role) &&
        status === PURCHASE_STATUS.DRAFT
    );
}