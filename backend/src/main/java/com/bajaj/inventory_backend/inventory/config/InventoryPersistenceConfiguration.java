package com.bajaj.inventory_backend.inventory.config;

import com.bajaj.inventory_backend.inventory.persistence.jdbc.PostgresAtomicStockStore;
import com.bajaj.inventory_backend.inventory.persistence.jdbc.PostgresInventoryQueries;
import com.bajaj.inventory_backend.inventory.persistence.jdbc.PostgresStockInitializer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Configuration(proxyBeanMethods = false)
public class InventoryPersistenceConfiguration {

    @Bean
    PostgresInventoryQueries postgresInventoryQueries(JdbcTemplate jdbc) {
        return new PostgresInventoryQueries(jdbc);
    }

    @Bean
    PostgresAtomicStockStore postgresAtomicStockStore(JdbcTemplate jdbc, PlatformTransactionManager manager) {
        return new PostgresAtomicStockStore(jdbc, transactionTemplate(jdbc, manager));
    }

    @Bean
    PostgresStockInitializer postgresStockInitializer(JdbcTemplate jdbc, PlatformTransactionManager manager) {
        return new PostgresStockInitializer(jdbc, transactionTemplate(jdbc, manager));
    }

    private TransactionTemplate transactionTemplate(JdbcTemplate jdbc, PlatformTransactionManager manager) {
        if (!(manager instanceof JpaTransactionManager jpa)
                || jpa.getDataSource() == null || jpa.getDataSource() != jdbc.getDataSource()) {
            throw new IllegalStateException("Inventory JDBC and JPA must share the application DataSource");
        }
        return new TransactionTemplate(manager);
    }
}
