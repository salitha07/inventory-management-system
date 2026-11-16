package com.bajaj.inventory_backend.inventory.persistence.jdbc;

import java.net.URI;

/** Test-only fail-closed settings validation; never reads application configuration. */
final class Member6PostgresSafety {
    static final String DATABASE = "inventory_m6_test";
    private Member6PostgresSafety() { }

    static void validate(String url, String user, String password) {
        if (url == null || user == null || user.isBlank() || password == null || password.isBlank()) {
            throw new IllegalArgumentException("Explicit M6_PG_TEST_URL/USER/PASSWORD are required");
        }
        try {
            if (!url.startsWith("jdbc:postgresql://")) throw new IllegalArgumentException();
            URI uri = URI.create(url.substring(5));
            if (!"postgresql".equals(uri.getScheme()) || uri.getHost() == null
                    || !('/' + DATABASE).equals(uri.getRawPath()) || uri.getRawQuery() != null
                    || uri.getRawFragment() != null || uri.getRawUserInfo() != null) {
                throw new IllegalArgumentException();
            }
        } catch (IllegalArgumentException ex) {
            // Do not include input URL or credentials in error messages.
            throw new IllegalArgumentException("Only a plain PostgreSQL URL for inventory_m6_test is permitted");
        }
    }

    static void validateSchema(String schema) {
        if (schema == null || !schema.matches("m6_it_[a-f0-9]{32}")) {
            throw new IllegalArgumentException("Unsafe Member 6 fixture schema");
        }
    }
}
