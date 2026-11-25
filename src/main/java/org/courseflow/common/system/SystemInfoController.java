package org.courseflow.common.system;

import io.swagger.v3.oas.annotations.Operation;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Duration;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/system")
public class SystemInfoController {
  private final SystemInfoService service;

  public SystemInfoController(SystemInfoService service) {
    this.service = service;
  }

  @GetMapping("/info")
  @Operation(
      summary = "Get administrator system diagnostics",
      description =
          "ADMIN only. Allowlisted diagnostic snapshot, cached for 60 seconds. "
              + "Unavailable fields are null; SMTP connectivity is not tested. No credentials are returned.")
  public ResponseEntity<SystemInfoResponse> info(
      HttpServletRequest request, @AuthenticationPrincipal Jwt jwt) {
    Long lifetime =
        jwt != null && jwt.getIssuedAt() != null && jwt.getExpiresAt() != null
            ? Duration.between(jwt.getIssuedAt(), jwt.getExpiresAt()).getSeconds()
            : null;
    var response =
        new SystemInfoResponse(
            service.snapshot(),
            new SystemInfoResponse.ServerEndpoint(request.getLocalAddr(), request.getLocalPort()),
            lifetime);
    return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(response);
  }
}
