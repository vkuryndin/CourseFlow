package org.courseflow.integration.business;

import static org.courseflow.integration.support.JsonChecks.requiredField;
import static org.courseflow.integration.support.JsonChecks.requiredInteger;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.Map;
import org.courseflow.integration.support.AdvancedHttpTest;
import org.junit.jupiter.api.Test;

/** Independent negative contracts: valid fixtures, exact error codes, committed database state. */
class RejectedOperationIT extends AdvancedHttpTest {
  @Test
  void duplicateRegistrationCannotCreateOrModifyAccount() throws Exception {
    String email = email();
    var payload = Map.of("email", email, "displayName", "Original", "password", PASSWORD);
    long id = id(expect(api("POST", "/api/auth/register", null, payload), 201));
    var user = jdbc.queryForMap("SELECT * FROM users WHERE id = ?", id);
    var account = jdbc.queryForMap("SELECT * FROM user_accounts WHERE user_id = ?", id);
    var duplicate = Map.of("email", email, "displayName", "Overwrite", "password", NEW_PASSWORD);
    error(api("POST", "/api/auth/register", null, duplicate), 409, "BUSINESS_CONFLICT");
    assertEquals(1L, count("SELECT count(*) FROM users WHERE email = ?", email));
    assertEquals(user, jdbc.queryForMap("SELECT * FROM users WHERE id = ?", id));
    assertEquals(account, jdbc.queryForMap("SELECT * FROM user_accounts WHERE user_id = ?", id));
  }

  @Test
  void anotherTeacherAndInvalidGradeCannotModifySubmission() throws Exception {
    Graph graph = graph();
    long assignment = assignment(graph.lesson());
    Actor learner = actor(List.of("STUDENT"));
    Actor otherTeacher = actor(List.of("TEACHER"));
    enroll(graph.course(), learner);
    Object submitted =
        expect(
            api(
                "POST",
                "/api/assignments/" + assignment + "/submissions",
                learner.token(),
                Map.of("content", "Original answer")),
            201);
    long id = id(submitted);
    String path = "/api/assignments/" + assignment + "/submissions/" + id + "/grade";
    var snapshot = jdbc.queryForMap("SELECT * FROM submissions WHERE id = ?", id);
    var grade =
        Map.of(
            "version", requiredInteger(submitted, "version"), "score", 70, "feedback", "Changed");
    error(api("PUT", path, otherTeacher.token(), grade), 403, "ACCESS_DENIED");
    assertEquals(snapshot, jdbc.queryForMap("SELECT * FROM submissions WHERE id = ?", id));
    error(
        api(
            "PUT",
            path,
            teacher.token(),
            Map.of("version", requiredInteger(submitted, "version"), "score", 101)),
        400,
        "BAD_REQUEST");
    assertEquals(snapshot, jdbc.queryForMap("SELECT * FROM submissions WHERE id = ?", id));
    Object valid = expect(api("PUT", path, teacher.token(), grade), 200);
    assertEquals(70L, requiredInteger(valid, "score"));
    assertEquals(
        70, jdbc.queryForObject("SELECT score FROM submissions WHERE id = ?", Integer.class, id));
  }

  private void error(Response response, int status, String code) {
    Object body = expect(response, status);
    assertEquals((long) status, requiredInteger(body, "status"));
    assertEquals(code, requiredField(body, "code"));
  }
}
