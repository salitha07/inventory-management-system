import { useState } from "react";
import { useNavigate } from "react-router-dom";
import { useAuth } from "../context/AuthContext";

function Login() {
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const { login } = useAuth();
  const navigate = useNavigate();

  const handleSubmit = (e) => {
    e.preventDefault();
    const token = "demo-access-token";
    login(token, { email: email || "cashier@bajaj.com", role: "CASHIER" });
    navigate("/sales/pos");
  };

  return (
    <div style={{ maxWidth: 420, margin: "60px auto", padding: 28, border: "1px solid #e2e8f0", borderRadius: 8, boxShadow: "0 4px 6px -1px rgba(0,0,0,0.1)" }}>
      <h1 style={{ fontSize: "1.3rem", color: "#1e293b", marginBottom: 8 }}>Bajaj Inventory Management System</h1>
      <h2 style={{ fontSize: "1.1rem", color: "#475569", marginBottom: 20 }}>Sign In</h2>

      <form onSubmit={handleSubmit}>
        <div style={{ marginBottom: 16 }}>
          <label style={{ display: "block", marginBottom: 6, fontWeight: 500 }}>Email</label>
          <input
            type="email"
            value={email}
            onChange={(e) => setEmail(e.target.value)}
            placeholder="Enter your email (e.g. cashier@bajaj.com)"
            required
            style={{ width: "100%", padding: "8px 12px", border: "1px solid #cbd5e1", borderRadius: 4, boxSizing: "border-box" }}
          />
        </div>

        <div style={{ marginBottom: 20 }}>
          <label style={{ display: "block", marginBottom: 6, fontWeight: 500 }}>Password</label>
          <input
            type="password"
            value={password}
            onChange={(e) => setPassword(e.target.value)}
            placeholder="Enter your password"
            required
            style={{ width: "100%", padding: "8px 12px", border: "1px solid #cbd5e1", borderRadius: 4, boxSizing: "border-box" }}
          />
        </div>

        <button
          type="submit"
          style={{ width: "100%", padding: "10px", backgroundColor: "#0284c7", color: "white", border: "none", borderRadius: 4, cursor: "pointer", fontWeight: 600 }}
        >
          Sign In
        </button>
      </form>
    </div>
  );
}

export default Login;