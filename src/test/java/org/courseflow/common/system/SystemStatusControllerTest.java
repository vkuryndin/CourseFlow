package org.courseflow.common.system;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.Test;

class SystemStatusControllerTest {

    @Test
    void returnsConnectedStatusAfterDatabaseCheck() {
        var service = mock(SystemStatusService.class);

        var response = new SystemStatusController(service).status();

        verify(service).checkDatabase();
        assertEquals("CourseFlow", response.application());
        assertEquals("UP", response.status());
        assertEquals("CONNECTED", response.database());
    }
}
