package org.courseflow.common.system;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.sql.Connection;
import java.sql.SQLException;
import javax.sql.DataSource;

import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

class SystemStatusServiceTest {
    private final DataSource ds = mock(DataSource.class);
    private final SystemStatusService service = new SystemStatusService(ds);

    @Test
    void healthyConnectionIsClosed() throws Exception {
        Connection c = mock(Connection.class);
        when(ds.getConnection()).thenReturn(c);
        when(c.isValid(3)).thenReturn(true);
        service.checkDatabase();

        verify(c).close();
    }

    @Test
    void invalidConnectionIs503AndClosed() throws Exception {
        Connection c = mock(Connection.class);
        when(ds.getConnection()).thenReturn(c);

        assertEquals(503, assertThrows(ResponseStatusException.class, service::checkDatabase).getStatusCode().value());
        verify(c).close();
    }

    @Test
    void connectionFailureIs503() throws Exception {
        when(ds.getConnection()).thenThrow(new SQLException("offline"));

        assertEquals(503, assertThrows(ResponseStatusException.class, service::checkDatabase).getStatusCode().value());
    }
}
