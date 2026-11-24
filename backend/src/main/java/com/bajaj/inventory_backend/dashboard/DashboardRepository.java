
package com.bajaj.inventory_backend.dashboard;

import com.bajaj.inventory_backend.dto.dashboard.LowStockAlertResponse;
import com.bajaj.inventory_backend.dto.dashboard.RecentActivityResponse;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;

@Repository
public class DashboardRepository {

    private final JdbcTemplate jdbcTemplate;

    public DashboardRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = Objects.requireNonNull(jdbcTemplate);
    }

    public BigDecimal getTodaySales() {
        String sql = """
                SELECT COALESCE(SUM(total), 0)
                FROM sales
                WHERE status = 'COMPLETED'
                  AND sale_date >= CURRENT_DATE
                  AND sale_date < CURRENT_DATE + INTERVAL '1 day'
                """;

        return jdbcTemplate.queryForObject(sql, BigDecimal.class);
    }

    public BigDecimal getMonthlySales() {
        String sql = """
                SELECT COALESCE(SUM(total), 0)
                FROM sales
                WHERE status = 'COMPLETED'
                  AND sale_date >= DATE_TRUNC('month', CURRENT_DATE)
                  AND sale_date < DATE_TRUNC('month', CURRENT_DATE)
                                      + INTERVAL '1 month'
                """;

        return jdbcTemplate.queryForObject(sql, BigDecimal.class);
    }

    public long getTotalProducts() {
        Long count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM products",
                Long.class
        );
        return count != null ? count : 0L;
    }

    public long getLowStockCount() {
        String sql = """
                SELECT COUNT(*)
                FROM inventory_stock s
                JOIN products p ON p.id = s.product_id
                WHERE p.active = TRUE
                  AND s.reorder_level IS NOT NULL
                  AND s.quantity <= s.reorder_level
                """;

        Long count = jdbcTemplate.queryForObject(sql, Long.class);
        return count != null ? count : 0L;
    }

    public List<LowStockAlertResponse> getLowStockProducts() {
        String sql = """
                SELECT p.id, p.product_name, p.product_code,
                       s.quantity, s.reorder_level
                FROM products p
                JOIN inventory_stock s ON s.product_id = p.id
                WHERE p.active = TRUE
                  AND s.reorder_level IS NOT NULL
                  AND s.quantity <= s.reorder_level
                ORDER BY p.product_name ASC
                """;

        return jdbcTemplate.query(sql, (rs, rowNum) ->
                new LowStockAlertResponse(
                        rs.getLong("id"),
                        rs.getString("product_name"),
                        rs.getString("product_code"),
                        rs.getInt("quantity"),
                        rs.getInt("reorder_level")
                )
        );
    }

    public List<RecentActivityResponse> getRecentSales() {
        String sql = """
                SELECT id, sale_number, status, total, sale_date
                FROM sales
                ORDER BY sale_date DESC, id DESC
                LIMIT 10
                """;

        return jdbcTemplate.query(sql, (rs, rowNum) ->
                new RecentActivityResponse(
                        rs.getLong("id"),
                        "SALE",
                        rs.getString("sale_number"),
                        "Sale status: " + rs.getString("status"),
                        rs.getBigDecimal("total"),
                        rs.getTimestamp("sale_date").toInstant()
                )
        );
    }
}
