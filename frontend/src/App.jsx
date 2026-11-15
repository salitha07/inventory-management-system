import { BrowserRouter, Routes, Route } from "react-router-dom";

import Login from "./pages/Login";
import Dashboard from "./pages/Dashboard";
import SalesManagement from "./pages/SalesManagement";
import POS from "./pages/POS";
import SalesHistory from "./pages/SalesHistory";
import Invoice from "./pages/Invoice";
import MainLayout from "./layouts/MainLayout";
import ProtectedRoute from "./routes/ProtectedRoute";
import { AuthProvider } from "./context/AuthContext";

function App() {
  return (
    <AuthProvider>
      <BrowserRouter>
        <Routes>
          {/* Public Routes */}
          <Route path="/login" element={<Login />} />

          {/* Protected Routes */}
          <Route element={<ProtectedRoute />}>
            <Route element={<MainLayout />}>
              <Route path="/" element={<Dashboard />} />
              <Route path="/sales" element={<SalesManagement />} />
              <Route path="/sales/pos" element={<POS />} />
              <Route path="/sales/history" element={<SalesHistory />} />
              <Route path="/sales/invoice/:id" element={<Invoice />} />
            </Route>
          </Route>
        </Routes>
      </BrowserRouter>
    </AuthProvider>
  );
}

export default App;