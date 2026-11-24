package org.courseflow.integration.orm;

import static org.junit.jupiter.api.Assertions.*;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import javax.sql.DataSource;
import org.courseflow.integration.support.AdvancedHttpTest;
import org.courseflow.integration.support.ConcurrentCalls;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/** Real independent JDBC transactions against the Testcontainers database. */
class TransactionIsolationIT extends AdvancedHttpTest {
    @Autowired private DataSource dataSource;

    @Test
    void readCommittedNeverReadsUncommittedChanges() throws Exception {
        long id = category("Original");
        try (Connection writer = transaction(Connection.TRANSACTION_READ_COMMITTED);
             Connection reader = transaction(Connection.TRANSACTION_READ_COMMITTED)) {
            try {
                change(writer, id, "Uncommitted");
                assertEquals("Original", description(reader, id));
                writer.rollback();
                assertEquals("Original", description(reader, id));
            } finally {
                reader.rollback();
                writer.rollback();
            }
        }
    }

    @Test
    void readCommittedSeesNewCommitOnNextStatement() throws Exception {
        long id = category("Original");
        try (Connection writer = transaction(Connection.TRANSACTION_READ_COMMITTED);
             Connection reader = transaction(Connection.TRANSACTION_READ_COMMITTED)) {
            try {
                assertEquals("Original", description(reader, id));
                change(writer, id, "Committed");
                writer.commit();
                assertEquals("Committed", description(reader, id));
            } finally {
                reader.rollback();
                writer.rollback();
            }
        }
    }

    @Test
    void repeatableReadKeepsSnapshotUntilTransactionEnds() throws Exception {
        long id = category("Original");
        try (Connection writer = transaction(Connection.TRANSACTION_READ_COMMITTED);
             Connection reader = transaction(Connection.TRANSACTION_REPEATABLE_READ)) {
            try {
                assertEquals("Original", description(reader, id));
                change(writer, id, "Committed");
                writer.commit();
                assertEquals("Original", description(reader, id));
                reader.commit();
                assertEquals("Committed", description(reader, id));
            } finally {
                reader.rollback();
                writer.rollback();
            }
        }
    }

    @Test
    void readCommittedCanObserveNewMatchingRows() throws Exception {
        phantomCheck(Connection.TRANSACTION_READ_COMMITTED, 1L);
    }

    @Test
    void repeatableReadPreventsPhantomsInSameSnapshot() throws Exception {
        phantomCheck(Connection.TRANSACTION_REPEATABLE_READ, 0L);
    }

    @Test
    void repeatableReadRejectsConcurrentUpdateOfStaleSnapshot() throws Exception {
        long id = category("Original");
        try (Connection a = transaction(Connection.TRANSACTION_REPEATABLE_READ);
             Connection b = transaction(Connection.TRANSACTION_READ_COMMITTED)) {
            try {
                assertEquals("Original", description(a, id));
                change(b, id, "Winner");
                b.commit();
                SQLException error = assertThrows(SQLException.class, () -> change(a, id, "Stale"));
                assertEquals("40001", error.getSQLState());
                a.rollback();
                assertEquals("Winner", description(a, id));
            } finally {
                a.rollback();
                b.rollback();
            }
        }
    }

    @Test
    void serializablePreventsWriteSkewAcrossDifferentRows() throws Exception {
        long first = category("Active");
        long second = category("Active");
        CountDownLatch snapshots = new CountDownLatch(2);
        var results = ConcurrentCalls.pair(
                () -> deactivateWithSerializableSnapshot(first, second, first, snapshots),
                () -> deactivateWithSerializableSnapshot(first, second, second, snapshots));
        assertEquals(1L, results.stream().filter("COMMITTED"::equals).count());
        assertEquals(1L, results.stream().filter("40001"::equals).count());
        assertEquals(1L, count("SELECT count(*) FROM categories WHERE id IN (?, ?) AND description = 'Active'", first, second));
    }

    private String deactivateWithSerializableSnapshot(long first, long second, long own, CountDownLatch barrier) throws Exception {
        try (Connection c = transaction(Connection.TRANSACTION_SERIALIZABLE)) {
            try {
                try (var statement = c.prepareStatement("SELECT count(*) FROM categories WHERE id IN (?, ?) AND description = 'Active'")) {
                    statement.setLong(1, first);
                    statement.setLong(2, second);
                    try (var rows = statement.executeQuery()) {
                        assertTrue(rows.next());
                        assertEquals(2L, rows.getLong(1));
                    }
                }
                barrier.countDown();
                assertTrue(barrier.await(10, TimeUnit.SECONDS), "Both transactions must read before updating");
                change(c, own, "Inactive");
                c.commit();
                return "COMMITTED";
            } catch (SQLException e) {
                c.rollback();
                if (!"40001".equals(e.getSQLState())) throw e;
                return e.getSQLState();
            } finally {
                c.rollback();
            }
        }
    }

    private void phantomCheck(int isolation, long expected) throws Exception {
        String name = "Phantom-" + unique();
        try (Connection reader = transaction(isolation)) {
            try {
                assertEquals(0L, matching(reader, name));
                expect(api("POST", "/api/categories", admin, Map.of("name", name, "description", "New row")), 201);
                assertEquals(expected, matching(reader, name));
                reader.commit();
                assertEquals(1L, matching(reader, name));
            } finally {
                reader.rollback();
            }
        }
    }

    private long category(String description) throws Exception {
        return id(expect(api("POST", "/api/categories", admin, Map.of("name", "Isolation-" + unique(), "description", description)), 201));
    }

    private Connection transaction(int isolation) throws SQLException {
        Connection c = dataSource.getConnection();
        try {
            c.setTransactionIsolation(isolation);
            c.setAutoCommit(false);
            try (var statement = c.createStatement()) {
                statement.execute("SET LOCAL statement_timeout = '15s'");
            }
            return c;
        } catch (SQLException e) {
            c.close();
            throw e;
        }
    }

    private static void change(Connection c, long id, String value) throws SQLException {
        try (var statement = c.prepareStatement("UPDATE categories SET description = ? WHERE id = ?")) {
            statement.setString(1, value);
            statement.setLong(2, id);
            assertEquals(1, statement.executeUpdate());
        }
    }

    private static String description(Connection c, long id) throws SQLException {
        try (var statement = c.prepareStatement("SELECT description FROM categories WHERE id = ?")) {
            statement.setLong(1, id);
            try (var rows = statement.executeQuery()) {
                assertTrue(rows.next());
                return rows.getString(1);
            }
        }
    }

    private static long matching(Connection c, String name) throws SQLException {
        try (var statement = c.prepareStatement("SELECT count(*) FROM categories WHERE name = ?")) {
            statement.setString(1, name);
            try (var rows = statement.executeQuery()) {
                assertTrue(rows.next());
                return rows.getLong(1);
            }
        }
    }
}
