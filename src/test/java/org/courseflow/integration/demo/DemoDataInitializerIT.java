package org.courseflow.integration.demo;

import org.courseflow.demo.DemoAccounts;
import org.courseflow.demo.DemoDataService;
import org.courseflow.integration.support.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.*;

class DemoDataInitializerIT extends AbstractIntegrationTest {
    @Autowired private DemoDataService service;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private PasswordEncoder encoder;

    @Test
    @Transactional(timeout = 300)
    void seedPreservesExistingDataAndNeverRecreatesDeletedLinks() {
        // Профиль test не запускает автоматическое заполнение. Транзакция теста откатывается.
        assertFalse(service.isInitialized());
        assertEquals(0, count("SELECT count(*) FROM users WHERE email LIKE '%@demo.courseflow.invalid'"));
        long usersBefore = count("SELECT count(*) FROM users");
        long coursesBefore = count("SELECT count(*) FROM courses");
        long adminBefore = count("SELECT count(*) FROM users WHERE system_admin");
        assertEquals(1, adminBefore);
        jdbc.update("INSERT INTO categories(name, description, version) VALUES (?, ?, 0)", "программирование", "Существующая категория");
        jdbc.update("INSERT INTO tags(name, version) VALUES (?, 0)", "java");
        long categoriesBefore = count("SELECT count(*) FROM categories");
        long tagsBefore = count("SELECT count(*) FROM tags");
        var accounts = DemoAccounts.create();
        assertTrue(service.seed(accounts));
        assertTrue(service.isInitialized());
        assertEquals(usersBefore + 60, count("SELECT count(*) FROM users"));
        assertEquals(coursesBefore + 16, count("SELECT count(*) FROM courses"));
        assertEquals(adminBefore, count("SELECT count(*) FROM users WHERE system_admin"));
        assertEquals(categoriesBefore + 7, count("SELECT count(*) FROM categories"));
        assertEquals(tagsBefore + 23, count("SELECT count(*) FROM tags"));
        assertEquals("Существующая категория", jdbc.queryForObject("SELECT description FROM categories WHERE name='программирование'", String.class));
        assertEquals(60, count("SELECT count(*) FROM user_accounts a JOIN users u ON u.id=a.user_id WHERE u.email LIKE '%@demo.courseflow.invalid'"));
        String hash = jdbc.queryForObject("SELECT a.password_hash FROM user_accounts a JOIN users u ON u.id=a.user_id WHERE u.email=?", String.class, accounts.get(0).email());
        assertTrue(encoder.matches(accounts.get(0).password(), hash));
        assertEquals(3, count("SELECT count(*) FROM users WHERE status='INACTIVE' AND email LIKE '%@demo.courseflow.invalid'"));
        assertEquals(48, count("SELECT count(*) FROM modules"));
        assertEquals(144, count("SELECT count(*) FROM lessons"));
        assertEquals(96, count("SELECT count(*) FROM lesson_sessions"));
        assertEquals(48, count("SELECT count(*) FROM assignments"));
        assertEquals(48, count("SELECT count(*) FROM quizzes"));
        assertEquals(480, count("SELECT count(*) FROM questions"));
        assertEquals(1920, count("SELECT count(*) FROM answer_options"));
        assertEquals(200, count("SELECT count(*) FROM enrollments"));
        assertEquals(400, count("SELECT count(*) FROM submissions"));
        assertEquals(600, count("SELECT count(*) FROM quiz_submissions"));
        assertEquals(100, count("SELECT count(*) FROM course_reviews"));
        assertEquals(0, count("SELECT count(*) FROM submissions s JOIN assignments a ON a.id=s.assignment_id WHERE s.submitted_at>=a.due_at OR s.score>a.max_score"));
        assertEquals(0, count("SELECT count(*) FROM quiz_submissions WHERE attempt_number>2 OR finished_at>expires_at"));
        assertEquals(0, count("""
                SELECT count(*) FROM quiz_submissions qs
                WHERE qs.status='SUBMITTED' AND qs.score <> 10 * (
                  SELECT count(*) FROM questions q WHERE q.quiz_id=qs.quiz_id
                    AND NOT EXISTS (SELECT 1 FROM answer_options ao WHERE ao.question_id=q.id
                      AND ao.is_correct <> (EXISTS (SELECT 1 FROM quiz_answer_selections sel
                        WHERE sel.quiz_submission_id=qs.id AND sel.answer_option_id=ao.id))))
                """));
        assertEquals(0, count("""
                SELECT count(*) FROM lesson_sessions a JOIN lesson_sessions b ON a.id<b.id
                JOIN lessons la ON la.id=a.lesson_id JOIN modules ma ON ma.id=la.module_id JOIN courses ca ON ca.id=ma.course_id
                JOIN lessons lb ON lb.id=b.lesson_id JOIN modules mb ON mb.id=lb.module_id JOIN courses cb ON cb.id=mb.course_id
                WHERE ca.teacher_id=cb.teacher_id AND a.starts_at<b.ends_at AND b.starts_at<a.ends_at
                """));
        long links = count("SELECT count(*) FROM course_tag");
        assertTrue(links > 0);
        jdbc.update("DELETE FROM course_tag WHERE (course_id,tag_id) IN (SELECT course_id,tag_id FROM course_tag LIMIT 1)");
        assertFalse(service.seed(DemoAccounts.create()));
        assertEquals(links - 1, count("SELECT count(*) FROM course_tag"));
        assertEquals(usersBefore + 60, count("SELECT count(*) FROM users"));
        assertEquals(coursesBefore + 16, count("SELECT count(*) FROM courses"));
    }

    @Test
    void uniqueViolationRollsBackPreviouslyInsertedDemoAccount() {
        assertFalse(service.isInitialized());
        long before = count("SELECT count(*) FROM users");
        var accounts = new java.util.ArrayList<>(DemoAccounts.create());
        accounts.set(1, accounts.get(0));
        RuntimeException error = assertThrows(RuntimeException.class, () -> service.seed(accounts));
        boolean duplicate = false;
        for (Throwable cause = error; cause != null; cause = cause.getCause()) {
            if (cause instanceof java.sql.SQLException sql && "23505".equals(sql.getSQLState())) {
                duplicate = true;
                break;
            }
        }
        assertTrue(duplicate, "Ожидалось нарушение уникальности email, SQLSTATE 23505");
        assertEquals(before, count("SELECT count(*) FROM users"));
        assertEquals(0, count("SELECT count(*) FROM users WHERE email LIKE '%@demo.courseflow.invalid'"));
        assertFalse(service.isInitialized());
    }

    private long count(String sql) {
        return jdbc.queryForObject(sql, Long.class);
    }
}
