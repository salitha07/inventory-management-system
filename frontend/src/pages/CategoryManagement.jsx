
import { useEffect, useState } from "react";
import categoryService from "../services/categoryService";
import { useAuth } from "../context/useAuth";

function CategoryManagement() {
    const { user } = useAuth();

    const [categories, setCategories] = useState([]);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState("");
    const [success, setSuccess] = useState("");

    const [page, setPage] = useState(0);
    const [totalPages, setTotalPages] = useState(0);

    const [showForm, setShowForm] = useState(false);
    const [editingCategory, setEditingCategory] = useState(null);

    const [formData, setFormData] = useState({
        categoryName: "",
        description: "",
    });

    const [saving, setSaving] = useState(false);

    const canManage =
        user?.role === "ADMIN" || user?.role === "MANAGER";

    // Load categories
    const loadCategories = async () => {
        try {
            setLoading(true);
            setError("");

            const response = await categoryService.getAllCategories({
                page,
                size: 10,
            });

            if (response.success) {
                setCategories(response.data.content || []);
                setTotalPages(response.data.totalPages || 0);
            } else {
                setError(response.message || "Failed to load categories.");
            }
        } catch (err) {
            setError(
                err.response?.data?.message ||
                "Unable to load categories. Please try again."
            );
        } finally {
            setLoading(false);
        }
    };

    useEffect(() => {
        loadCategories();
    }, [page]);

    // Open form for creating a category
    const handleAdd = () => {
        setEditingCategory(null);
        setFormData({
            categoryName: "",
            description: "",
        });
        setError("");
        setSuccess("");
        setShowForm(true);
    };

    // Open form for editing a category
    const handleEdit = (category) => {
        setEditingCategory(category);
        setFormData({
            categoryName: category.categoryName,
            description: category.description || "",
        });
        setError("");
        setSuccess("");
        setShowForm(true);
    };

    // Handle input changes
    const handleChange = (e) => {
        const { name, value } = e.target;

        setFormData((previous) => ({
            ...previous,
            [name]: value,
        }));
    };

    // Create or update category
    const handleSubmit = async (e) => {
        e.preventDefault();

        setError("");
        setSuccess("");

        const categoryName = formData.categoryName.trim();
        const description = formData.description.trim();

        if (!categoryName) {
            setError("Category name is required.");
            return;
        }

        try {
            setSaving(true);

            const categoryData = {
                categoryName,
                description,
            };

            let response;

            if (editingCategory) {
                response = await categoryService.updateCategory(
                    editingCategory.id,
                    categoryData
                );
            } else {
                response = await categoryService.createCategory(
                    categoryData
                );
            }

            if (response.success) {
                setShowForm(false);
                setSuccess(
                    editingCategory
                        ? "Category updated successfully."
                        : "Category created successfully."
                );

                await loadCategories();
            } else {
                setError(response.message || "Operation failed.");
            }
        } catch (err) {
            setError(
                err.response?.data?.message ||
                "Unable to save category. Please try again."
            );
        } finally {
            setSaving(false);
        }
    };

    // Activate or deactivate category
    const handleStatusChange = async (category) => {
        const action = category.active ? "deactivate" : "activate";

        const confirmed = window.confirm(
            `Are you sure you want to ${action} "${category.categoryName}"?`
        );

        if (!confirmed) return;

        setError("");
        setSuccess("");

        try {
            const response =
                await categoryService.updateCategoryStatus(
                    category.id,
                    !category.active
                );

            if (response.success) {
                setSuccess(
                    `Category ${action}d successfully.`
                );
                await loadCategories();
            } else {
                setError(response.message || "Status update failed.");
            }
        } catch (err) {
            setError(
                err.response?.data?.message ||
                "Unable to update category status."
            );
        }
    };

    return (
        <div style={styles.container}>
            <div style={styles.header}>
                <div>
                    <h1 style={styles.title}>Category Management</h1>
                    <p style={styles.subtitle}>
                        Create and manage product categories.
                    </p>
                </div>

                {canManage && (
                    <button style={styles.primaryButton} onClick={handleAdd}>
                        + Add Category
                    </button>
                )}
            </div>

            {error && <div style={styles.error}>{error}</div>}
            {success && <div style={styles.success}>{success}</div>}

            {showForm && (
                <div style={styles.formCard}>
                    <h2>
                        {editingCategory ? "Edit Category" : "Add New Category"}
                    </h2>

                    <form onSubmit={handleSubmit}>
                        <div style={styles.formGroup}>
                            <label style={styles.label}>Category Name *</label>
                            <input
                                type="text"
                                name="categoryName"
                                value={formData.categoryName}
                                onChange={handleChange}
                                maxLength={100}
                                placeholder="Enter category name"
                                required
                                style={styles.input}
                            />
                        </div>

                        <div style={styles.formGroup}>
                            <label style={styles.label}>Description</label>
                            <textarea
                                name="description"
                                value={formData.description}
                                onChange={handleChange}
                                maxLength={1000}
                                placeholder="Enter category description"
                                rows={3}
                                style={styles.input}
                            />
                        </div>

                        <div style={styles.formActions}>
                            <button
                                type="submit"
                                disabled={saving}
                                style={styles.primaryButton}
                            >
                                {saving
                                    ? "Saving..."
                                    : editingCategory
                                        ? "Update Category"
                                        : "Create Category"}
                            </button>

                            <button
                                type="button"
                                onClick={() => setShowForm(false)}
                                style={styles.secondaryButton}
                            >
                                Cancel
                            </button>
                        </div>
                    </form>
                </div>
            )}

            <div style={styles.tableCard}>
                <h2 style={styles.sectionTitle}>Categories</h2>

                {loading ? (
                    <p>Loading categories...</p>
                ) : categories.length === 0 ? (
                    <p>No categories found.</p>
                ) : (
                    <div style={{ overflowX: "auto" }}>
                        <table style={styles.table}>
                            <thead>
                                <tr>
                                    <th style={styles.th}>ID</th>
                                    <th style={styles.th}>Category Name</th>
                                    <th style={styles.th}>Description</th>
                                    <th style={styles.th}>Status</th>
                                    {canManage && (
                                        <th style={styles.th}>Actions</th>
                                    )}
                                </tr>
                            </thead>

                            <tbody>
                                {categories.map((category) => (
                                    <tr key={category.id}>
                                        <td style={styles.td}>{category.id}</td>
                                        <td style={styles.td}>
                                            {category.categoryName}
                                        </td>
                                        <td style={styles.td}>
                                            {category.description || "-"}
                                        </td>
                                        <td style={styles.td}>
                                            <span
                                                style={{
                                                    ...styles.status,
                                                    backgroundColor: category.active
                                                        ? "#dcfce7"
                                                        : "#fee2e2",
                                                    color: category.active
                                                        ? "#166534"
                                                        : "#991b1b",
                                                }}
                                            >
                                                {category.active
                                                    ? "Active"
                                                    : "Inactive"}
                                            </span>
                                        </td>

                                        {canManage && (
                                            <td style={styles.td}>
                                                <button
                                                    onClick={() => handleEdit(category)}
                                                    style={styles.editButton}
                                                >
                                                    Edit
                                                </button>

                                                <button
                                                    onClick={() =>
                                                        handleStatusChange(category)
                                                    }
                                                    style={styles.statusButton}
                                                >
                                                    {category.active
                                                        ? "Deactivate"
                                                        : "Activate"}
                                                </button>
                                            </td>
                                        )}
                                    </tr>
                                ))}
                            </tbody>
                        </table>
                    </div>
                )}

                <div style={styles.pagination}>
                    <button
                        onClick={() => setPage((previous) => previous - 1)}
                        disabled={page === 0 || loading}
                        style={styles.secondaryButton}
                    >
                        Previous
                    </button>

                    <span>
                        Page {page + 1} of {Math.max(totalPages, 1)}
                    </span>

                    <button
                        onClick={() => setPage((previous) => previous + 1)}
                        disabled={page + 1 >= totalPages || loading}
                        style={styles.secondaryButton}
                    >
                        Next
                    </button>
                </div>
            </div>
        </div>
    );
}

const styles = {
    container: {
        padding: "30px",
        maxWidth: "1200px",
        margin: "0 auto",
        color: "#1f2937",
    },
    header: {
        display: "flex",
        justifyContent: "space-between",
        alignItems: "center",
        gap: "15px",
        marginBottom: "25px",
        flexWrap: "wrap",
    },
    title: {
        fontSize: "28px",
        margin: "0 0 8px",
    },
    subtitle: {
        color: "#6b7280",
    },
    primaryButton: {
        backgroundColor: "#2563eb",
        color: "white",
        border: "none",
        padding: "11px 18px",
        borderRadius: "6px",
        cursor: "pointer",
    },
    secondaryButton: {
        backgroundColor: "#e5e7eb",
        color: "#1f2937",
        border: "none",
        padding: "9px 14px",
        borderRadius: "6px",
        cursor: "pointer",
    },
    formCard: {
        backgroundColor: "white",
        padding: "24px",
        borderRadius: "10px",
        marginBottom: "25px",
        boxShadow: "0 2px 8px rgba(0,0,0,0.08)",
    },
    formGroup: {
        marginBottom: "18px",
    },
    label: {
        display: "block",
        marginBottom: "7px",
        fontWeight: "600",
    },
    input: {
        width: "100%",
        padding: "11px",
        border: "1px solid #d1d5db",
        borderRadius: "6px",
        boxSizing: "border-box",
        fontSize: "14px",
    },
    formActions: {
        display: "flex",
        gap: "10px",
        marginTop: "15px",
    },
    tableCard: {
        backgroundColor: "white",
        padding: "24px",
        borderRadius: "10px",
        boxShadow: "0 2px 8px rgba(0,0,0,0.08)",
    },
    sectionTitle: {
        marginTop: 0,
        marginBottom: "20px",
    },
    table: {
        width: "100%",
        borderCollapse: "collapse",
        textAlign: "left",
    },
    th: {
        backgroundColor: "#f3f4f6",
        padding: "13px",
        borderBottom: "1px solid #e5e7eb",
    },
    td: {
        padding: "13px",
        borderBottom: "1px solid #e5e7eb",
    },
    status: {
        padding: "5px 10px",
        borderRadius: "20px",
        fontSize: "12px",
        fontWeight: "600",
    },
    editButton: {
        padding: "7px 10px",
        marginRight: "6px",
        border: "none",
        borderRadius: "5px",
        backgroundColor: "#dbeafe",
        color: "#1d4ed8",
        cursor: "pointer",
    },
    statusButton: {
        padding: "7px 10px",
        border: "none",
        borderRadius: "5px",
        backgroundColor: "#f3f4f6",
        cursor: "pointer",
    },
    pagination: {
        display: "flex",
        justifyContent: "center",
        alignItems: "center",
        gap: "20px",
        marginTop: "25px",
    },
    error: {
        backgroundColor: "#fee2e2",
        color: "#991b1b",
        padding: "12px",
        borderRadius: "6px",
        marginBottom: "15px",
    },
    success: {
        backgroundColor: "#dcfce7",
        color: "#166534",
        padding: "12px",
        borderRadius: "6px",
        marginBottom: "15px",
    },
};

export default CategoryManagement;
