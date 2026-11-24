package com.bajaj.inventory_backend.inventory.config;

import com.bajaj.inventory_backend.inventory.core.InventoryQueries;
import com.bajaj.inventory_backend.inventory.persistence.jdbc.PostgresAtomicStockStore;
import com.bajaj.inventory_backend.inventory.persistence.jdbc.PostgresInventoryQueries;
import com.bajaj.inventory_backend.inventory.persistence.jdbc.PostgresStockInitializer;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.transaction.PlatformTransactionManager;

import javax.sql.DataSource;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class InventoryPersistenceConfigurationTest {
    @Test
    void registersExistingAdaptersWithoutOpeningAConnection() {
        DataSource dataSource = mock(DataSource.class);
        JpaTransactionManager manager = mock(JpaTransactionManager.class);
        when(manager.getDataSource()).thenReturn(dataSource);
        try (var context = new AnnotationConfigApplicationContext()) {
            context.registerBean(JdbcTemplate.class, () -> new JdbcTemplate(dataSource));
            context.registerBean(PlatformTransactionManager.class, () -> manager);
            context.register(InventoryPersistenceConfiguration.class);
            context.refresh();

            assertSame(context.getBean(PostgresInventoryQueries.class), context.getBean(InventoryQueries.class));
            assertNotNull(context.getBean(PostgresAtomicStockStore.class));
            assertNotNull(context.getBean(PostgresStockInitializer.class));
            verifyNoInteractions(dataSource);
        }
    }

    @Test
    void rejectsASeparateDataSourceForWrites() {
        JdbcTemplate jdbc = new JdbcTemplate(mock(DataSource.class));
        JpaTransactionManager manager = mock(JpaTransactionManager.class);
        when(manager.getDataSource()).thenReturn(mock(DataSource.class));

        assertThrows(IllegalStateException.class,
                () -> new InventoryPersistenceConfiguration().postgresAtomicStockStore(jdbc, manager));
    }
}
