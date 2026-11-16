package com.bajaj.inventory_backend.inventory.persistence.jdbc;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

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
}
