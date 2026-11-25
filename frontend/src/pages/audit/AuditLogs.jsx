
import { useEffect, useState } from "react";
import auditService from "../../services/auditService";
import { useAuth } from "../../context/useAuth";
import "./AuditLogs.css";

function AuditLogs() {
  const { user } = useAuth();

  const [logs, setLogs] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");
  const [page, setPage] = useState(0);
  const [totalPages, setTotalPages] = useState(0);

  const isAdmin = user?.role === "ADMIN";

  useEffect(() => {
    const loadAuditLogs = async () => {
      try {
        setLoading(true);
        setError("");

        const data = await auditService.getAllAuditLogs(page, 10);

        setLogs(data.content || []);
        setTotalPages(data.totalPages || 0);
      } catch (err) {
        setError(
          err.response?.data?.message ||
          "Unable to load audit logs."
        );
      } finally {
        setLoading(false);
      }
    };

    if (isAdmin) {
      loadAuditLogs();
    } else {
      setLoading(false);
    }
  }, [page, isAdmin]);

  if (!isAdmin) {
    return (
      <div className="audit-page">
        <h2>Access Denied</h2>
        <p>You do not have permission to view audit logs.</p>
      </div>
    );
  }

  return (
    <div className="audit-page">
      <div className="audit-header">
        <div>
          <h1>Audit Logs</h1>
          <p>Monitor and review system activities.</p>
        </div>
      </div>

      <div className="audit-table-card">
        <h2>System Activity History</h2>

        {error && <div className="audit-error">{error}</div>}

        {loading ? (
          <p>Loading audit logs...</p>
        ) : (
          <>
            <div className="audit-table-wrapper">
              <table>
                <thead>
                  <tr>
                    <th>ID</th>
                    <th>User ID</th>
                    <th>Action</th>
                    <th>Entity</th>
                    <th>Entity ID</th>
                    <th>Description</th>
                    <th>Date & Time</th>
                  </tr>
                </thead>

                <tbody>
                  {logs.length > 0 ? (
                    logs.map((log) => (
                      <tr key={log.id}>
                        <td>{log.id}</td>
                        <td>{log.userId ?? "System"}</td>
                        <td>
                          <span className="audit-action">
                            {log.action}
                          </span>
                        </td>
                        <td>{log.entityType || "-"}</td>
                        <td>{log.entityId ?? "-"}</td>
                        <td>{log.description || "-"}</td>
                        <td>
                          {log.createdAt
                            ? new Date(log.createdAt).toLocaleString()
                            : "-"}
                        </td>
                      </tr>
                    ))
                  ) : (
                    <tr>
                      <td colSpan="7">No audit logs found.</td>
                    </tr>
                  )}
                </tbody>
              </table>
            </div>

            <div className="audit-pagination">
              <button
                disabled={page === 0}
                onClick={() => setPage((p) => p - 1)}
              >
                Previous
              </button>

              <span>
                Page {totalPages === 0 ? 0 : page + 1} of {totalPages}
              </span>

              <button
                disabled={page + 1 >= totalPages}
                onClick={() => setPage((p) => p + 1)}
              >
                Next
              </button>
            </div>
          </>
        )}
      </div>
    </div>
  );
}

export default AuditLogs;
