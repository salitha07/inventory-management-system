# Member 6 opt-in PostgreSQL adapter tests

These are adapter-level fixture tests, not verification of production V1/V2/V6 migrations,
JPA/Sales transaction sharing, authentication or return eligibility. No application context,
application.properties, Flyway or default DataSource is used. No new dependencies are needed.

## Explicit setup

Have a database administrator provision a disposable database named exactly
`inventory_m6_test` and a dedicated non-superuser test role that can connect and create
schemas there. The harness never creates/drops databases. Prefer a role with access only
to this database, without access to application databases. Do not use production credentials.

Set these environment variables in your test shell through your usual secret-management
mechanism (do not commit passwords or include them in command arguments):

- `M6_PG_TEST_URL`: plain `jdbc:postgresql://localhost:5432/inventory_m6_test`
- `M6_PG_TEST_USER`: the dedicated test role
- `M6_PG_TEST_PASSWORD`: its password

Missing settings fail before any connection. Only the exact database name is allowed;
inventory_db and all other names are rejected. URL query parameters, encoded database names,
embedded credentials and fragments are rejected. Every actual connection also verifies
current_database() before schema/fixture writes. Do not use a proxy mapping the test database
name to application storage; actual database identity must match.

## Run explicitly with Java 21

From backend, with JAVA_HOME selecting Java 21:

```powershell
.\mvnw.cmd '-Dm6.pg.integration=true' '-Dtest=PostgresAtomicStockStoreIT' test
```

Two gates prevent ordinary execution: the IT class name is outside default Surefire test
patterns, and JUnit requires `m6.pg.integration=true`. Explicit class selection without the
enable property skips the class. Enabling the property alone does not select the IT class.
Even broad explicit test selection cannot connect without the enable flag and validated settings.
Safety-validator unit tests run normally without a database.

## Isolation and cleanup

Each test creates a fresh unpredictable `m6_it_<32 hex digits>` schema, with no IF NOT EXISTS.
Connections set search_path to only that schema; there is no public fallback. Fixture DDL
is under src/test/resources/inventory, outside Flyway paths, with minimal products/users
identity tables and balance/movement constraints copied from the inactive draft. It does not
claim full Product/User schema fidelity. No teammate entity is copied.

Cleanup runs only for the validated random schema successfully created by this test, after
worker termination. It explicitly drops schema-qualified stock_movements, inventory_stock,
products and users, in that order, each with DROP TABLE IF EXISTS ... RESTRICT. Movement
foreign keys reference products/users; stock references products, so children are removed
before parents. IF EXISTS tolerates tables absent after incomplete fixture setup. The
movement table's owned identity sequence is removed automatically with its owning table.
Finally DROP SCHEMA ... RESTRICT requires the schema to be empty. No cascading drop is used.

An unexpected dependency stops cleanup immediately; unknown remaining schema objects make
the final schema drop fail. Errors propagate with the generated schema name for manual
investigation. Earlier restrictive drops may already have completed; cleanup is not an
all-or-nothing transaction. Cleanup never expands into public/shared objects to resolve
dependencies. No automatic wildcard discovery or deletion is performed.
Lock and statement timeouts bound worker operations. If the process is killed or cleanup
fails, the owned schema may remain; inspect and remove that exact test schema manually in
inventory_m6_test after verifying ownership. Do not use broad wildcard cleanup commands.
Connection establishment and administrative schema creation/cleanup timeout coverage remain
incomplete; the scoped connection timeouts do not bound all phases of the harness.

## Coverage and interpretation

The eight IT methods cover receipt/history/metadata/time, deduction/exact depletion,
fixture-approved returns and manual correction, forced movement-insert rollback, caller
rollback, concurrent independent deductions, distinct missing-Product/stock errors, and
constraints/rejected operations without history. Two concurrent workers request four each
from five units: exactly one must commit, leaving one unit and one movement.
The start barrier does not guarantee deterministic overlap or lock contention; a passing
run is not a deterministic demonstration that one worker waited on the other's row lock.

The tests use a test-only JDBC transaction manager, correctly coordinating only their own
DataSource. Passing them would not establish atomicity with the application's eventual JPA
transaction manager. Nor do they establish idempotency, production policy authorization,
Product-active locking, initialization lifecycle or migration compatibility.

At implementation time M6_PG_TEST_URL/USER/PASSWORD were absent. No PostgreSQL connection
or real IT execution was attempted. Compilation and database-free test results are reported
separately. Product/V2, approved active V6 and shared Sales contracts remain integration work.
