package org.courseflow.common.system;

import java.sql.Connection;
import java.sql.SQLException;
import javax.sql.DataSource;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class SystemStatusService {

  private final DataSource dataSource;

  public SystemStatusService(DataSource dataSource) {
    this.dataSource = dataSource;
  }

  public void checkDatabase() {
    try (Connection connection = dataSource.getConnection()) {
      if (!connection.isValid(3)) {
        throw new ResponseStatusException(
            HttpStatus.SERVICE_UNAVAILABLE, "PostgreSQL connection is unavailable");
      }
    } catch (SQLException exception) {
      throw new ResponseStatusException(
          HttpStatus.SERVICE_UNAVAILABLE, "PostgreSQL connection is unavailable", exception);
    }
  }
}
