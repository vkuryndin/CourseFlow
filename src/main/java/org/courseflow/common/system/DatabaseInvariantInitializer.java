package org.courseflow.common.system;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.Ordered;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** PostgreSQL invariants not expressible as portable JPA UNIQUE constraints. */
@Component
public class DatabaseInvariantInitializer implements ApplicationRunner, Ordered {

    private final JdbcTemplate jdbcTemplate;

    public DatabaseInvariantInitializer(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        // Fail startup if legacy duplicates exist; never delete data automatically.
        jdbcTemplate.execute("""
                CREATE UNIQUE INDEX IF NOT EXISTS ux_tags_lower_name
                ON tags (lower(name))
                """);
        jdbcTemplate.execute("""
                CREATE UNIQUE INDEX IF NOT EXISTS ux_users_single_system_admin
                ON users (system_admin) WHERE system_admin = true
                """);
    }
}
