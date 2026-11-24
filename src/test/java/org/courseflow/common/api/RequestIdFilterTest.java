package org.courseflow.common.api;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.UUID;

import jakarta.servlet.ServletException;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class RequestIdFilterTest {

    @Test
    void createsServerSideRequestIdAndCleansMdc() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(RequestIdFilter.HEADER, "untrusted");
        MockHttpServletResponse response = new MockHttpServletResponse();
        new RequestIdFilter().doFilter(request, response, (req, res) -> {
            String id = response.getHeader(RequestIdFilter.HEADER);
            assertNotNull(id);
            assertDoesNotThrow(() -> UUID.fromString(id));
            assertNotEquals("untrusted", id);
            assertEquals(id, RequestIdFilter.requestId(request));
            assertEquals(id, MDC.get("requestId"));
        });

        assertNull(MDC.get("requestId"));
    }

    @Test
    void restoresPreviousMdcEvenOnFailure() {
        MDC.put("requestId", "previous");
        try {
            assertThrows(ServletException.class,
                    () -> new RequestIdFilter().doFilter(new MockHttpServletRequest(), new MockHttpServletResponse(),
                    (req, res) -> {
                throw new ServletException("test");
            }));
            assertEquals("previous", MDC.get("requestId"));
        } finally {
            MDC.remove("requestId");
        }
    }
}
