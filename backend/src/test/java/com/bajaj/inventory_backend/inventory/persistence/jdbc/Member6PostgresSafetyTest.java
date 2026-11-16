package com.bajaj.inventory_backend.inventory.persistence.jdbc;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.dao.DataIntegrityViolationException;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class Member6PostgresSafetyTest {
    @Test void acceptsOnlyDedicatedExplicitConnection() {
        assertDoesNotThrow(() -> Member6PostgresSafety.validate("jdbc:postgresql://localhost:5432/inventory_m6_test", "test_user", "test-only-value"));
        for (String url : new String[] {"jdbc:postgresql://localhost/inventory_db",
                "jdbc:postgresql://localhost/other_test", "jdbc:postgresql:inventory_m6_test",
                "jdbc:postgresql://localhost/inventory_m6_test?currentSchema=public",
                "jdbc:postgresql://user:secret@localhost/inventory_m6_test",
                "jdbc:postgresql://localhost/inventory_m6_test#fragment",
                "jdbc:postgresql://localhost/%69nventory_m6_test"}) {
            assertThrows(IllegalArgumentException.class, () -> Member6PostgresSafety.validate(url, "test_user", "test-only-value"));
        }
    }
    @Test void missingCredentialsAndUnsafeSchemasFailClosed() {
        assertThrows(IllegalArgumentException.class, () -> Member6PostgresSafety.validate(null, null, null));
        assertThrows(IllegalArgumentException.class, () -> Member6PostgresSafety.validate("jdbc:postgresql://localhost/inventory_m6_test", "test_user", ""));
        for (String schema : new String[] {"public", "m6_it_shared", "m6_it_x; DROP SCHEMA public"}) {
            assertThrows(IllegalArgumentException.class, () -> Member6PostgresSafety.validateSchema(schema));
        }
        assertDoesNotThrow(() -> Member6PostgresSafety.validateSchema("m6_it_" + "a".repeat(32)));
    }

    @Test void cleanupIsQualifiedRestrictiveAndInDependencyOrder() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        String schema = "m6_it_" + "a".repeat(32);
        PostgresAtomicStockStoreIT.dropFixtures(jdbc, schema);
        var order = inOrder(jdbc);
        for (String table : new String[] {"stock_movements", "inventory_stock", "products", "users"}) {
            order.verify(jdbc).execute("DROP TABLE IF EXISTS " + schema + "." + table + " RESTRICT");
        }
        order.verify(jdbc).execute("DROP SCHEMA " + schema + " RESTRICT");
        verifyNoMoreInteractions(jdbc);
    }

    @Test void cleanupRejectsPublicAndStopsAtUnexpectedDependency() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        assertThrows(IllegalArgumentException.class, () -> PostgresAtomicStockStoreIT.dropFixtures(jdbc, "public"));
        verifyNoInteractions(jdbc);
        String schema = "m6_it_" + "b".repeat(32);
        var error = new DataIntegrityViolationException("Unexpected dependent object");
        doThrow(error).when(jdbc).execute("DROP TABLE IF EXISTS " + schema + ".stock_movements RESTRICT");
        assertSame(error, assertThrows(DataIntegrityViolationException.class,
                () -> PostgresAtomicStockStoreIT.dropFixtures(jdbc, schema)));
        verify(jdbc).execute("DROP TABLE IF EXISTS " + schema + ".stock_movements RESTRICT");
        verifyNoMoreInteractions(jdbc);
    }
}
