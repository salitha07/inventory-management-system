import api from "./api";

/**
 * Dashboard API service.
 * Uses the shared Axios instance with JWT authentication.
 */

export const getDashboardSummary = async () => {
  const response = await api.get("/dashboard/summary");
  return response.data;
};

export const getLowStockAlerts = async () => {
  const response = await api.get("/dashboard/low-stock");
  return response.data;
};

export const getRecentActivity = async () => {
  const response = await api.get("/dashboard/recent-activity");
  return response.data;
};

export default {
  getDashboardSummary,
  getLowStockAlerts,
  getRecentActivity,
};
