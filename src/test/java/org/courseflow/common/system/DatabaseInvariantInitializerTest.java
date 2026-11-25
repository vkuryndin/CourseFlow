package org.courseflow.common.system;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.core.Ordered;
import org.springframework.jdbc.core.JdbcTemplate;

class DatabaseInvariantInitializerTest {
  @Test
  void acquiresAdvisoryLockBeforeIssuingSchemaStatements() {
    JdbcTemplate jdbc = mock(JdbcTemplate.class);
    var initializer = new DatabaseInvariantInitializer(jdbc);
    initializer.run(null);
    var sql = ArgumentCaptor.forClass(String.class);
    verify(jdbc, times(39)).execute(sql.capture());
    var statements = sql.getAllValues();
    assertEquals(Ordered.HIGHEST_PRECEDENCE, initializer.getOrder());
    assertEquals("SELECT pg_advisory_xact_lock(62130001)", statements.get(0));
    assertTrue(statements.stream().anyMatch(s -> s.contains("ux_tags_lower_name")));
    assertTrue(statements.stream().anyMatch(s -> s.contains("fk_cf_selection_option_question")));
    assertTrue(statements.stream().anyMatch(s -> s.contains("ck_cf_review_rating")));
  }

  @Test
  void failedInitializationDoesNotContinueSilently() {
    JdbcTemplate jdbc = mock(JdbcTemplate.class);
    doThrow(new IllegalStateException("Unavailable"))
        .when(jdbc)
        .execute("SELECT pg_advisory_xact_lock(62130001)");
    assertThrows(
        IllegalStateException.class, () -> new DatabaseInvariantInitializer(jdbc).run(null));
    verify(jdbc).execute("SELECT pg_advisory_xact_lock(62130001)");
    verifyNoMoreInteractions(jdbc);
  }
}
