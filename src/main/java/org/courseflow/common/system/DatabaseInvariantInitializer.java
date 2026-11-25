package org.courseflow.common.system;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.Ordered;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** PostgreSQL indexes and invariants maintained alongside the current JPA schema. */
@Component
public class DatabaseInvariantInitializer implements ApplicationRunner, Ordered {

  private final JdbcTemplate jdbcTemplate;

  public DatabaseInvariantInitializer(JdbcTemplate jdbcTemplate) {
    this.jdbcTemplate = jdbcTemplate;
  }

  @Override
  public int getOrder() {
    return Ordered.HIGHEST_PRECEDENCE;
  }

  @Override
  @Transactional
  public void run(ApplicationArguments args) {
    // Serialize this initializer across instances (after Hibernate schema setup).
    jdbcTemplate.execute("SELECT pg_advisory_xact_lock(62130001)");
    // Fail startup if legacy duplicates exist; never delete data automatically.
    jdbcTemplate.execute(
        """
                CREATE UNIQUE INDEX IF NOT EXISTS ux_tags_lower_name
                ON tags (lower(name))
                """);
    jdbcTemplate.execute(
        """
                CREATE UNIQUE INDEX IF NOT EXISTS ux_users_single_system_admin
                ON users (system_admin) WHERE system_admin = true
                """);

    // Indexes for joins, filters and credential cleanup. No changes to table count.
    jdbcTemplate.execute(
        "CREATE INDEX IF NOT EXISTS ix_cf_courses_teacher_status ON courses (teacher_id, status)");
    jdbcTemplate.execute(
        "CREATE INDEX IF NOT EXISTS ix_cf_courses_category_status ON courses (category_id, status)");
    jdbcTemplate.execute(
        "CREATE INDEX IF NOT EXISTS ix_cf_enrollments_course_status ON enrollments (course_id, status)");
    jdbcTemplate.execute(
        "CREATE INDEX IF NOT EXISTS ix_cf_enrollments_student_status ON enrollments (student_id, status)");
    jdbcTemplate.execute(
        "CREATE INDEX IF NOT EXISTS ix_cf_sessions_lesson_start ON lesson_sessions (lesson_id, starts_at)");
    jdbcTemplate.execute(
        "CREATE INDEX IF NOT EXISTS ix_cf_assignments_lesson_due ON assignments (lesson_id, due_at)");
    jdbcTemplate.execute(
        "CREATE INDEX IF NOT EXISTS ix_cf_submissions_assignment_status ON submissions (assignment_id, status)");
    jdbcTemplate.execute(
        "CREATE INDEX IF NOT EXISTS ix_cf_submissions_student_date ON submissions (student_id, submitted_at)");
    jdbcTemplate.execute(
        "CREATE INDEX IF NOT EXISTS ix_cf_quiz_submissions_quiz_status ON quiz_submissions (quiz_id, status, expires_at)");
    jdbcTemplate.execute(
        "CREATE INDEX IF NOT EXISTS ix_cf_quiz_submissions_expiring ON quiz_submissions (expires_at, id) WHERE status = 'IN_PROGRESS'");
    jdbcTemplate.execute(
        "CREATE INDEX IF NOT EXISTS ix_cf_quiz_submissions_student_date ON quiz_submissions (student_id, taken_at)");
    jdbcTemplate.execute(
        "CREATE INDEX IF NOT EXISTS ix_cf_auth_sessions_account_revoked ON auth_sessions (user_account_id, revoked)");
    jdbcTemplate.execute(
        "CREATE INDEX IF NOT EXISTS ix_cf_auth_sessions_expires ON auth_sessions (expires_at)");
    jdbcTemplate.execute(
        "CREATE INDEX IF NOT EXISTS ix_cf_reset_tokens_account_expires ON password_reset_tokens (user_account_id, expires_at)");
    jdbcTemplate.execute(
        "CREATE INDEX IF NOT EXISTS ix_cf_reset_tokens_expires ON password_reset_tokens (expires_at)");
    jdbcTemplate.execute("CREATE INDEX IF NOT EXISTS ix_cf_course_tag_tag ON course_tag (tag_id)");
    jdbcTemplate.execute(
        "CREATE INDEX IF NOT EXISTS ix_cf_user_roles_role ON user_roles (role, user_id)");
    jdbcTemplate.execute(
        "CREATE INDEX IF NOT EXISTS ix_cf_answer_options_question ON answer_options (question_id)");

    jdbcTemplate.execute(
        "CREATE UNIQUE INDEX IF NOT EXISTS ux_cf_modules_course_position ON modules (course_id, position)");
    jdbcTemplate.execute(
        "CREATE UNIQUE INDEX IF NOT EXISTS ux_cf_lessons_module_position ON lessons (module_id, position)");
    jdbcTemplate.execute(
        "CREATE UNIQUE INDEX IF NOT EXISTS ux_cf_categories_lower_name ON categories (lower(name))");
    jdbcTemplate.execute(
        "CREATE UNIQUE INDEX IF NOT EXISTS ux_cf_questions_quiz_position ON questions (quiz_id, position)");
    // PostgreSQL requires a unique target for the composite foreign key.
    jdbcTemplate.execute(
        "CREATE UNIQUE INDEX IF NOT EXISTS ux_cf_answer_options_id_question ON answer_options (id, question_id)");

    addConstraint("courses", "ck_cf_course_duration", "CHECK (duration > 0)");
    addConstraint("modules", "ck_cf_module_position", "CHECK (position > 0)");
    addConstraint("lessons", "ck_cf_lesson_position", "CHECK (position > 0)");
    addConstraint("lesson_sessions", "ck_cf_session_times", "CHECK (ends_at > starts_at)");
    addConstraint("assignments", "ck_cf_assignment_max_score", "CHECK (max_score > 0)");
    addConstraint(
        "quizzes",
        "ck_cf_quiz_limits",
        "CHECK (time_limit BETWEEN 1 AND 240 AND max_attempts BETWEEN 1 AND 10)");
    addConstraint("questions", "ck_cf_question_position", "CHECK (position > 0)");
    addConstraint("submissions", "ck_cf_submission_score", "CHECK (score IS NULL OR score >= 0)");
    addConstraint("quiz_submissions", "ck_cf_quiz_attempt_number", "CHECK (attempt_number > 0)");
    addConstraint(
        "quiz_submissions", "ck_cf_quiz_score", "CHECK (score IS NULL OR score BETWEEN 0 AND 100)");
    addConstraint("quiz_submissions", "ck_cf_quiz_times", "CHECK (expires_at > taken_at)");
    addConstraint("course_reviews", "ck_cf_review_rating", "CHECK (rating BETWEEN 1 AND 5)");
    addConstraint(
        "quiz_answer_selections",
        "fk_cf_selection_option_question",
        "FOREIGN KEY (answer_option_id, question_id) REFERENCES answer_options (id, question_id)");
  }

  // All arguments are fixed internal DDL constants, never request parameters.
  // Validate existing rows as well; fail rather than silently repair legacy data.
  private void addConstraint(String table, String name, String definition) {
    jdbcTemplate.execute(
        """
                DO $$
                BEGIN
                    IF NOT EXISTS (
                        SELECT 1 FROM pg_constraint
                        WHERE conrelid = '%s'::regclass AND conname = '%s'
                    ) THEN
                        ALTER TABLE %s ADD CONSTRAINT %s %s;
                    END IF;
                END;
                $$
                """
            .formatted(table, name, table, name, definition));
  }
}
