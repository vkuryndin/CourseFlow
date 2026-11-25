package org.courseflow.testing;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;

import jakarta.persistence.EntityManager;
import java.util.List;
import org.junit.jupiter.api.Test;

class QueryMocksTest {
  @Test
  void rejectsMissingExpectedParameter() {
    EntityManager em = mock(EntityManager.class);
    var query = QueryMocks.query(em, String.class, List.of(), "studentId");
    assertSame(query, em.createQuery("SELECT u.email FROM User u WHERE u.id = :studentId", String.class));
    assertThrows(AssertionError.class, () -> em.createQuery("SELECT u.email FROM User u", String.class));
    assertThrows(AssertionError.class, () -> em.createQuery("SELECT u.email FROM User u WHERE u.id = :studentIdOther", String.class));
  }
}
