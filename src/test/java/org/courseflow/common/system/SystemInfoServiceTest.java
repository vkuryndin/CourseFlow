package org.courseflow.common.system;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.zaxxer.hikari.HikariDataSource;
import com.zaxxer.hikari.HikariPoolMXBean;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

class SystemInfoServiceTest {
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-11-24T12:00:00Z"), ZoneOffset.UTC);

    @Test
    void readsActualDatabaseIdentityAndCachesSnapshot() throws SQLException {
        HikariDataSource dataSource = mock(HikariDataSource.class);
        Connection connection = mock(Connection.class);
        DatabaseMetaData metadata = mock(DatabaseMetaData.class);
        Statement identity = mock(Statement.class), size = mock(Statement.class), tables = mock(Statement.class);
        ResultSet identityRow = mock(ResultSet.class), sizeRow = mock(ResultSet.class), tableRow = mock(ResultSet.class);
        HikariPoolMXBean stats = mock(HikariPoolMXBean.class);
        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.getMetaData()).thenReturn(metadata);
        when(metadata.getDatabaseProductName()).thenReturn("PostgreSQL");
        when(metadata.getDatabaseProductVersion()).thenReturn("18.1");
        when(metadata.getDriverName()).thenReturn("PostgreSQL JDBC Driver");
        when(metadata.getDriverVersion()).thenReturn("42.7.8");
        when(connection.createStatement()).thenReturn(identity, size, tables);
        when(identity.executeQuery(SystemInfoService.IDENTITY_SQL)).thenReturn(identityRow);
        when(identityRow.next()).thenReturn(true);
        when(identityRow.getString(1)).thenReturn("courseflow");
        when(identityRow.getString(2)).thenReturn("appuser");
        when(identityRow.getString(3)).thenReturn("public");
        when(identityRow.getString(4)).thenReturn("192.168.1.10");
        when(identityRow.getInt(5)).thenReturn(5432);
        when(size.executeQuery(SystemInfoService.SIZE_SQL)).thenReturn(sizeRow);
        when(sizeRow.next()).thenReturn(true);
        when(sizeRow.getLong(1)).thenReturn(1048576L);
        when(tables.executeQuery(SystemInfoService.TABLE_COUNT_SQL)).thenReturn(tableRow);
        when(tableRow.next()).thenReturn(true);
        when(tableRow.getLong(1)).thenReturn(22L);
        when(dataSource.getPoolName()).thenReturn("HikariPool-test");
        when(dataSource.getMinimumIdle()).thenReturn(2);
        when(dataSource.getMaximumPoolSize()).thenReturn(10);
        when(dataSource.getConnectionTimeout()).thenReturn(5000L);
        when(dataSource.getHikariPoolMXBean()).thenReturn(stats);
        when(stats.getActiveConnections()).thenReturn(3);
        var environment = new MockEnvironment().withProperty("spring.mail.host", "smtp.example.invalid")
                .withProperty("spring.mail.port", "587")
                .withProperty("spring.mail.properties.mail.smtp.starttls.enable", "true")
                .withProperty("spring.datasource.password", "NEVER_EXPOSE_DB_PASSWORD")
                .withProperty("spring.mail.password", "NEVER_EXPOSE_MAIL_PASSWORD")
                .withProperty("JWT_SECRET_BASE64", "NEVER_EXPOSE_JWT_SECRET")
                .withProperty("spring.datasource.url", "jdbc:postgresql://secret?password=hidden");
        var service = new SystemInfoService(dataSource, environment, CLOCK);
        var snapshot = service.snapshot();
        assertSame(snapshot, service.snapshot());
        assertEquals(CLOCK.instant(), snapshot.collectedAt());
        assertEquals(60, snapshot.cacheSeconds());
        assertEquals("CONNECTED", snapshot.database().status());
        assertEquals("192.168.1.10", snapshot.database().serverAddress());
        assertEquals(5432, snapshot.database().serverPort());
        assertEquals("courseflow", snapshot.database().databaseName());
        assertEquals("appuser", snapshot.database().databaseUser());
        assertEquals(22L, snapshot.database().tableCount());
        assertEquals(1048576L, snapshot.database().sizeBytes());
        assertEquals(3, snapshot.pool().active());
        assertEquals(10, snapshot.pool().maximumSize());
        assertEquals("smtp.example.invalid", snapshot.mail().host());
        assertEquals(Boolean.TRUE, snapshot.mail().startTlsEnabled());
        assertEquals("NOT_CHECKED", snapshot.mail().status());
        assertFalse(snapshot.toString().contains("NEVER_EXPOSE"));
        assertFalse(snapshot.toString().contains("jdbc:postgresql"));
        verify(dataSource, times(1)).getConnection();
        verify(connection).close();
        verify(identity).setQueryTimeout(3);
        verify(size).setQueryTimeout(3);
        verify(tables).setQueryTimeout(3);
    }

    @Test
    void databaseFailureDoesNotExposeExceptionOrBreakOtherSections() throws SQLException {
        DataSource dataSource = mock(DataSource.class);
        when(dataSource.getConnection()).thenThrow(new SQLException("SECRET in JDBC error"));
        var snapshot = new SystemInfoService(dataSource, new MockEnvironment(), CLOCK).snapshot();
        assertEquals("UNAVAILABLE", snapshot.database().status());
        assertNull(snapshot.database().serverAddress());
        assertNull(snapshot.database().tableCount());
        assertNotNull(snapshot.runtime().javaVersion());
        assertNotNull(snapshot.application().springBootVersion());
        assertEquals("NOT_CHECKED", snapshot.mail().status());
        assertFalse(snapshot.toString().contains("SECRET"));
    }

    @Test
    void optionalSqlFailurePreservesIdentityAndOtherMetric() throws SQLException {
        DataSource dataSource = mock(DataSource.class);
        Connection connection = mock(Connection.class);
        DatabaseMetaData metadata = mock(DatabaseMetaData.class);
        Statement identity = mock(Statement.class), size = mock(Statement.class), tables = mock(Statement.class);
        ResultSet identityRow = mock(ResultSet.class), tableRow = mock(ResultSet.class);
        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.getMetaData()).thenReturn(metadata);
        when(connection.createStatement()).thenReturn(identity, size, tables);
        when(identity.executeQuery(SystemInfoService.IDENTITY_SQL)).thenReturn(identityRow);
        when(identityRow.next()).thenReturn(true);
        when(identityRow.getString(1)).thenReturn("courseflow");
        when(identityRow.wasNull()).thenReturn(true); // Unix socket: no TCP port.
        when(size.executeQuery(SystemInfoService.SIZE_SQL)).thenThrow(new SQLException("permission denied"));
        when(tables.executeQuery(SystemInfoService.TABLE_COUNT_SQL)).thenReturn(tableRow);
        when(tableRow.next()).thenReturn(true);
        when(tableRow.getLong(1)).thenReturn(22L);
        var db = new SystemInfoService(dataSource, new MockEnvironment(), CLOCK).snapshot().database();
        assertEquals("PARTIAL", db.status());
        assertEquals("courseflow", db.databaseName());
        assertNull(db.sizeBytes());
        assertNull(db.serverPort());
        assertEquals(22L, db.tableCount());
        verify(connection).close();
    }
}
