import { useState } from "react";
import { useNavigate, Link } from "react-router-dom";
import { useAuth } from "../context/useAuth";
import api from "../services/api";
import "./Login.css";

function Login() {
    const navigate = useNavigate();
    const { login } = useAuth();

    const [email, setEmail] = useState("");
    const [password, setPassword] = useState("");
    const [rememberMe, setRememberMe] = useState(false);
    const [showPassword, setShowPassword] = useState(false);
    const [error, setError] = useState("");
    const [loading, setLoading] = useState(false);

    const handleSubmit = async (e) => {
        e.preventDefault();

        setError("");
        setLoading(true);

        try {
            const response = await api.post("/auth/login", {
                email,
                password
            });

            const data = response.data;

            login(data.accessToken, data.user);

            navigate("/");
        } catch (err) {
            setError(
                err.response?.data?.message ||
                "Invalid email or password. Please try again."
            );
        } finally {
            setLoading(false);
        }
    };

    return (
        <div className="login-page">

            <section className="login-brand">

                <div className="brand-content">

                    <div className="bajaj-logo">
                        <span className="bajaj-symbol">➤</span>
                        <span>BAJAJ</span>
                    </div>

                    <div className="brand-divider"></div>

                    <h1>
                        CHATHURANGA spare parts
                        <br />
                        <span className="brand-subtitle">
                            Inventory &amp; Sales
                        </span>
                        <br />
                        <span>Management System</span>
                    </h1>

                    <p className="brand-tagline">
                        Manage <span>•</span> Track <span>•</span> Grow
                    </p>

                    <div className="brand-features">

                        <div className="feature-card">
                            <span>📦</span>
                            <p>Smart<br />Inventory</p>
                        </div>

                        <div className="feature-card">
                            <span>📊</span>
                            <p>Sales<br />Analytics</p>
                        </div>

                        <div className="feature-card">
                            <span>🛡️</span>
                            <p>Secure<br />Access</p>
                        </div>

                        <div className="feature-card">
                            <span>🤝</span>
                            <p>Better<br />Business</p>
                        </div>

                    </div>

                    <div className="brand-bottom">
                        <div className="motorcycle-illustration">
                            🏍️
                        </div>

                        <p>Your business, managed smarter.</p>
                    </div>

                </div>

            </section>

            <section className="login-form-section">

                <div className="login-card">

                    <div className="login-heading">
                        <h2>Welcome Back!</h2>
                        <p>Sign in to your account</p>
                        <div className="heading-line"></div>
                    </div>

                    <form onSubmit={handleSubmit}>

                        <div className="form-group">

                            <label htmlFor="email">
                                Email Address
                            </label>

                            <div className="input-wrapper">
                                <span>✉</span>

                                <input
                                    id="email"
                                    type="email"
                                    placeholder="Enter your email address"
                                    value={email}
                                    onChange={(e) => setEmail(e.target.value)}
                                    required
                                />
                            </div>

                        </div>

                        <div className="form-group">

                            <label htmlFor="password">
                                Password
                            </label>

                            <div className="input-wrapper">
                                <span>🔒</span>

                                <input
                                    id="password"
                                    type={showPassword ? "text" : "password"}
                                    placeholder="Enter your password"
                                    value={password}
                                    onChange={(e) => setPassword(e.target.value)}
                                    required
                                />

                                <button
                                    type="button"
                                    className="password-toggle"
                                    onClick={() =>
                                        setShowPassword(!showPassword)
                                    }
                                >
                                    {showPassword ? "Hide" : "Show"}
                                </button>

                            </div>

                        </div>

                        <div className="login-options">

                            <label className="remember-option">
                                <input
                                    type="checkbox"
                                    checked={rememberMe}
                                    onChange={(e) =>
                                        setRememberMe(e.target.checked)
                                    }
                                />

                                Remember me
                            </label>

                            <Link to="/forgot-password">
                                Forgot Password?
                            </Link>

                        </div>

                        {error && (
                            <div className="login-error">
                                {error}
                            </div>
                        )}

                        <button
                            type="submit"
                            className="sign-in-button"
                            disabled={loading}
                        >
                            {loading ? "Signing in..." : "Sign In"}
                        </button>

                    </form>

                    <div className="login-footer">
                        <p>Don't have an account?</p>
                        <span>Contact Administrator</span>
                    </div>

                </div>

                <p className="copyright">
                    © 2026 Bajaj Inventory System. All rights reserved.
                </p>

            </section>

        </div>
    );
}

export default Login;