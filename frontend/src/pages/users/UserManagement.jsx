import { useEffect, useState } from "react";
import userService from "../../services/userService";
import { useAuth } from "../../context/useAuth";
import "./UserManagement.css";

function UserManagement() {

    const { user } = useAuth();

    const [users, setUsers] = useState([]);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState("");
    const [search, setSearch] = useState("");
    const [roleFilter, setRoleFilter] = useState("ALL");

    const [showForm, setShowForm] = useState(false);
    const [editingUser, setEditingUser] = useState(null);

    const initialForm = {
        fullName: "",
        email: "",
        password: "",
        role: "CASHIER"
    };

    const [form, setForm] = useState(initialForm);

    const canManageUsers = ["ADMIN", "MANAGER"].includes(user?.role);

    const loadUsers = async () => {
        try {
            setLoading(true);
            setError("");

            const data = await userService.getAllUsers();
            setUsers(data);

        } catch (err) {
            setError(
                err.response?.data?.message ||
                "Unable to load users."
            );
        } finally {
            setLoading(false);
        }
    };

   useEffect(() => {
    let ignore = false;

    const fetchUsers = async () => {
        try {
            const data = await userService.getAllUsers();

            if (!ignore) {
                setUsers(data);
            }
        } catch (err) {
            if (!ignore) {
                setError(
                    err.response?.data?.message ||
                    "Unable to load users."
                );
            }
        } finally {
            if (!ignore) {
                setLoading(false);
            }
        }
    };

    fetchUsers();

    return () => {
        ignore = true;
    };
}, []);

    const handleChange = (e) => {
        setForm({
            ...form,
            [e.target.name]: e.target.value
        });
    };

    const openCreateForm = () => {
        setEditingUser(null);
        setForm(initialForm);
        setShowForm(true);
    };

    const openEditForm = (selectedUser) => {
        setEditingUser(selectedUser);

        setForm({
            fullName: selectedUser.fullName,
            email: selectedUser.email,
            password: "",
            role: selectedUser.role
        });

        setShowForm(true);
    };

    const handleSubmit = async (e) => {
        e.preventDefault();

        try {
            setError("");

            if (editingUser) {
                const updateData = {
                    fullName: form.fullName,
                    email: form.email,
                    role: form.role,
                    password: form.password || null
                };

                await userService.updateUser(
                    editingUser.id,
                    updateData
                );

                alert("User updated successfully!");

            } else {
                await userService.createUser(form);

                alert("User created successfully!");
            }

            setShowForm(false);
            setEditingUser(null);
            setForm(initialForm);

            await loadUsers();

        } catch (err) {
            setError(
                err.response?.data?.message ||
                "Unable to save user."
            );
        }
    };

    const handleStatusChange = async (selectedUser) => {

        const action = selectedUser.active
            ? "deactivate"
            : "activate";

        if (!window.confirm(
            `Are you sure you want to ${action} ${selectedUser.fullName}?`
        )) {
            return;
        }

        try {
            await userService.updateUserStatus(
                selectedUser.id,
                !selectedUser.active
            );

            await loadUsers();

        } catch (err) {
            setError(
                err.response?.data?.message ||
                "Unable to update account status."
            );
        }
    };

    const filteredUsers = users.filter((item) => {

        const matchesSearch =
            item.fullName.toLowerCase().includes(search.toLowerCase()) ||
            item.email.toLowerCase().includes(search.toLowerCase());

        const matchesRole =
            roleFilter === "ALL" || item.role === roleFilter;

        return matchesSearch && matchesRole;
    });

    if (!canManageUsers) {
        return <div className="users-page">
            <h2>Access Denied</h2>
            <p>You do not have permission to manage users.</p>
        </div>;
    }

    return (
        <div className="users-page">

            <div className="users-header">
                <div>
                    <h1>User Management</h1>
                    <p>Manage system users and their permissions.</p>
                </div>

                <button
                    className="primary-button"
                    onClick={openCreateForm}
                >
                    + Add New User
                </button>
            </div>

            {error && (
                <div className="error-message">
                    {error}
                </div>
            )}

            <div className="users-filters">

                <input
                    type="text"
                    placeholder="Search by name or email..."
                    value={search}
                    onChange={(e) => setSearch(e.target.value)}
                />

                <select
                    value={roleFilter}
                    onChange={(e) => setRoleFilter(e.target.value)}
                >
                    <option value="ALL">All Roles</option>
                    <option value="ADMIN">Admin</option>
                    <option value="MANAGER">Manager</option>
                    <option value="CASHIER">Cashier</option>
                    <option value="INVENTORY_STAFF">
                        Inventory Staff
                    </option>
                </select>

            </div>

            {showForm && (

                <div className="user-form-card">

                    <h2>
                        {editingUser ? "Edit User" : "Create New User"}
                    </h2>

                    <form onSubmit={handleSubmit}>

                        <label>Full Name</label>
                        <input
                            name="fullName"
                            value={form.fullName}
                            onChange={handleChange}
                            required
                        />

                        <label>Email</label>
                        <input
                            type="email"
                            name="email"
                            value={form.email}
                            onChange={handleChange}
                            required
                        />

                        <label>
                            {editingUser
                                ? "New Password (optional)"
                                : "Password"}
                        </label>

                        <input
                            type="password"
                            name="password"
                            value={form.password}
                            onChange={handleChange}
                            required={!editingUser}
                            minLength={8}
                            placeholder={
                                editingUser
                                    ? "Leave blank to keep current password"
                                    : "Minimum 8 characters"
                            }
                        />

                        <label>Role</label>
                        <select
                            name="role"
                            value={form.role}
                            onChange={handleChange}
                            required
                        >
                            <option value="ADMIN">Admin</option>
                            <option value="MANAGER">Manager</option>
                            <option value="CASHIER">Cashier</option>
                            <option value="INVENTORY_STAFF">
                                Inventory Staff
                            </option>
                        </select>

                        <div className="form-actions">

                            <button
                                type="submit"
                                className="primary-button"
                            >
                                {editingUser ? "Save Changes" : "Create User"}
                            </button>

                            <button
                                type="button"
                                className="secondary-button"
                                onClick={() => setShowForm(false)}
                            >
                                Cancel
                            </button>

                        </div>

                    </form>

                </div>
            )}

            <div className="users-table-card">

                <div className="table-heading">
    <div>
        <h2>System Users</h2>
        <p>View and manage registered system accounts.</p>
    </div>

    <span className="module-count">
        {filteredUsers.length} Users
    </span>
</div>

                {loading ? (
                    <p>Loading users...</p>
                ) : (

                    <div className="table-wrapper">

                        <table>

                            <thead>
                                <tr>
                                    <th>ID</th>
                                    <th>Name</th>
                                    <th>Email</th>
                                    <th>Role</th>
                                    <th>Status</th>
                                    <th>Actions</th>
                                </tr>
                            </thead>

                            <tbody>

                                {filteredUsers.map((item) => (

                                    <tr key={item.id}>

                                        <td>{item.id}</td>
                                        <td>{item.fullName}</td>
                                        <td>{item.email}</td>
                                        <td>
    <span className="role-badge">
        {item.role.replace("_", " ")}
    </span>
</td>

                                        <td>
                                            <span className={
                                                item.active
                                                    ? "status-active"
                                                    : "status-inactive"
                                            }>
                                                {item.active
                                                    ? "Active"
                                                    : "Inactive"}
                                            </span>
                                        </td>

                                        <td>
                                            <div className="action-buttons">

                                                <button
                                                    onClick={() => openEditForm(item)}
                                                    className="edit-button"
                                                >
                                                    Edit
                                                </button>

                                                <button
                                                    onClick={() => handleStatusChange(item)}
                                                    className="status-button"
                                                >
                                                    {item.active
                                                        ? "Deactivate"
                                                        : "Activate"}
                                                </button>

                                            </div>
                                        </td>

                                    </tr>

                                ))}

                                {filteredUsers.length === 0 && (
                                    <tr>
                                        <td colSpan="6">
                                            No users found.
                                        </td>
                                    </tr>
                                )}

                            </tbody>

                        </table>

                    </div>
                )}

            </div>

        </div>
    );
}

export default UserManagement;