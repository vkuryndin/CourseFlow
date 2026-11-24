package org.courseflow.common.system;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.oauth2.jwt.Jwt;

class SystemInfoControllerTest {
    @Test
    void returnsLocalEndpointAndTokenLifetimeWithoutCachingHttpResponse() {
        var service = mock(SystemInfoService.class);
        var snapshot = new SystemInfoResponse.Snapshot(Instant.EPOCH, 60, null, null, null, null, null);
        when(service.snapshot()).thenReturn(snapshot);
        var request = new MockHttpServletRequest("GET", "/api/system/info");
        request.setLocalAddr("127.0.0.1");
        request.setLocalPort(8080);
        var jwt = Jwt.withTokenValue("test").header("alg", "HS256")
                .issuedAt(Instant.EPOCH).expiresAt(Instant.EPOCH.plusSeconds(3600)).build();
        var response = new SystemInfoController(service).info(request, jwt);
        assertEquals("no-store", response.getHeaders().getFirst(HttpHeaders.CACHE_CONTROL));
        var body = response.getBody();
        assertNotNull(body);
        assertSame(snapshot, body.snapshot());
        assertEquals("127.0.0.1", body.serverEndpoint().localAddress());
        assertEquals(8080, body.serverEndpoint().localPort());
        assertEquals(3600L, body.accessTokenLifetimeSeconds());
        verify(service).snapshot();
    }
}
