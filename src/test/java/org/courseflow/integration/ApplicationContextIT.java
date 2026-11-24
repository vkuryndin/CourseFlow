package org.courseflow.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.courseflow.integration.support.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

class ApplicationContextIT extends AbstractIntegrationTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void shouldLoadApplicationContext() {
        assertEquals(1, jdbcTemplate.queryForObject(
                "SELECT 1", Integer.class
        ));
    }

    @Test
    void shouldUseTemporaryDatabase() {
        String database = jdbcTemplate.queryForObject(
                "SELECT current_database()", String.class
        );

        assertEquals("courseflow_test", database);
    }

    @Test
    void shouldUsePostgres18() {
        String version = jdbcTemplate.queryForObject(
                "SELECT version()", String.class
        );

        assertTrue(version.startsWith("PostgreSQL 18"));
    }

    @Test
    void shouldCreateAllDatabaseTables() {
        Long tableCount = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM information_schema.tables
                WHERE table_schema = 'public'
                  AND table_type = 'BASE TABLE'
                """,
                Long.class
        );

        assertEquals(22L, tableCount);
    }
}