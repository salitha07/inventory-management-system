function Login() {
  return (
    <div>
      <h1>Bajaj Inventory Management System</h1>
      <h2>Sign In</h2>

      <form>
        <div>
          <label>Email</label>
          <input
            type="email"
            placeholder="Enter your email"
            required
          />
        </div>

        <div>
          <label>Password</label>
          <input
            type="password"
            placeholder="Enter your password"
            required
          />
        </div>

        <button type="submit">Sign In</button>
      </form>
    </div>
  );
}

export default Login;