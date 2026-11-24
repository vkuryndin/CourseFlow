package org.courseflow.demo;

import jakarta.persistence.EntityManager;
import org.courseflow.assessment.entity.AnswerOption;
import org.courseflow.assessment.entity.Question;
import org.courseflow.assessment.entity.Quiz;
import org.courseflow.assessment.entity.QuizSubmission;
import org.courseflow.auth.entity.UserAccount;
import org.courseflow.catalog.entity.Category;
import org.courseflow.catalog.entity.Course;
import org.courseflow.catalog.entity.Tag;
import org.courseflow.content.entity.Lesson;
import org.courseflow.content.entity.LessonSession;
import org.courseflow.content.entity.LessonSessionFormat;
import org.courseflow.content.entity.Module;
import org.courseflow.identity.entity.Profile;
import org.courseflow.identity.entity.User;
import org.courseflow.learning.entity.Assignment;
import org.courseflow.learning.entity.Enrollment;
import org.courseflow.learning.entity.Submission;
import org.courseflow.review.entity.CourseReview;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Isolation;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
public class DemoDataService {
    private final EntityManager em;
    private final JdbcTemplate jdbc;
    private final PasswordEncoder encoder;
    private final Clock clock;

    public DemoDataService(EntityManager em, JdbcTemplate jdbc, PasswordEncoder encoder, Clock clock) {
        this.em = em;
        this.jdbc = jdbc;
        this.encoder = encoder;
        this.clock = clock;
    }

    /** Последовательность — устойчивый маркер в БД, а не новая бизнес-таблица. */
    public boolean isInitialized() {
        return Boolean.TRUE.equals(jdbc.queryForObject(
                "SELECT to_regclass('courseflow_demo_seed_v1') IS NOT NULL", Boolean.class));
    }

    /** Инициализация целиком фиксируется или откатывается вместе с маркером. */
    @Transactional(isolation = Isolation.READ_COMMITTED, timeout = 300)
    public boolean seed(List<DemoAccounts.Account> accounts) {
        jdbc.execute("SELECT pg_advisory_xact_lock(62130001)");
        if (isInitialized()) return false;
        if (accounts.size() != 60) throw new IllegalArgumentException("Ожидается 60 демонстрационных аккаунтов");
        List<DemoContent.CourseData> content = DemoContent.load();
        Instant now = clock.instant();
        LocalDate today = LocalDate.ofInstant(now, ZoneOffset.UTC);
        List<User> users = new ArrayList<>();
        for (DemoAccounts.Account account : accounts) {
            Instant created = now.minus(120, ChronoUnit.DAYS);
            User user = save(new User(account.name(), account.email(), account.roles(), created));
            Profile profile = new Profile(user);
            profile.update(account.bio(), null);
            save(profile);
            save(new UserAccount(user, encoder.encode(account.password()), created));
            users.add(user);
        }
        Map<String, Category> categories = new HashMap<>();
        Map<String, Tag> tags = new HashMap<>();
        List<SeedCourse> courses = new ArrayList<>();
        for (int i = 0; i < content.size(); i++) {
            DemoContent.CourseData data = content.get(i);
            // Последние два курса — архивные; все их занятия уже завершены.
            LocalDate start = today.minusDays(i >= 14 ? 80 : 23 + (i % 4) * 7L);
            Course course = save(new Course(data.title(), data.description(),
                    category(categories, data.category()), users.get(2 + i / 2), 42, start));
            for (String name : data.tags()) course.addTag(tag(tags, name));
            List<Assignment> assignments = new ArrayList<>();
            List<Quiz> quizzes = new ArrayList<>();
            for (int m = 0; m < 3; m++) {
                DemoContent.ModuleData moduleData = data.modules().get(m);
                Module module = save(course.addModule(moduleData.title(), moduleData.description(), m + 1));
                List<Lesson> lessons = new ArrayList<>();
                for (int l = 0; l < 3; l++) {
                    DemoContent.LessonData lesson = moduleData.lessons().get(l);
                    lessons.add(save(module.addLesson(lesson.title(), lesson.content(), null, l + 1)));
                }
                for (int session = 0; session < 2; session++) {
                    Instant starts = start.plusDays(m * 14L + session * 2L)
                            .atTime(i % 2 == 0 ? 10 : 14, 0).toInstant(ZoneOffset.UTC);
                    // OFFLINE не требует URL. ONLINE содержит безопасный демонстрационный адрес.
                    LessonSessionFormat format = session == 0 ? LessonSessionFormat.OFFLINE : LessonSessionFormat.ONLINE;
                    String url = session == 0 ? null : "https://meeting.example.invalid/course-" + (i + 1) + "-" + (m + 1);
                    save(new LessonSession(lessons.get(session), starts, starts.plus(90, ChronoUnit.MINUTES), format, url));
                }
                Instant deadline = start.plusDays(m * 14L + 10).atTime(18, 0).toInstant(ZoneOffset.UTC);
                assignments.add(save(new Assignment(lessons.get(2), "Практика: " + moduleData.title(), moduleData.assignment(), deadline, 100)));
                Quiz quiz = save(new Quiz(module, "Проверка знаний: " + moduleData.title(), 25));
                int position = 1;
                for (DemoContent.QuestionData questionData : moduleData.questions()) {
                    Question question = save(quiz.addQuestion(questionData.text(), questionData.type(), position++));
                    for (DemoContent.OptionData option : questionData.options()) {
                        save(question.addAnswerOption(option.text(), option.correct()));
                    }
                }
                quizzes.add(quiz);
            }
            courses.add(new SeedCourse(course, data, assignments, quizzes));
        }
        // 50 студентов × 4 различных курса = 200 записей. Разный прогресс на каждом курсе.
        for (int s = 0; s < 50; s++) {
            User student = users.get(s + 10);
            for (int e = 0; e < 4; e++) {
                SeedCourse item = courses.get((s * 3 + e * 4) % courses.size());
                Instant enrolled = item.course().getStartDate().minusDays(2).atStartOfDay().toInstant(ZoneOffset.UTC);
                Enrollment enrollment = save(Enrollment.createForStudent(student, item.course(), enrolled));
                populateActivity(item, student, s, e, now);
                if (e >= 2) {
                    int rating = 1 + (s + e * 2) % 5;
                    save(new CourseReview(item.course(), student, rating, review(item.data(), rating, s), now.minus(4 + s % 5, ChronoUnit.DAYS)));
                }
                if (e == 0 && s % 10 == 0) enrollment.withdraw(now.minus(1, ChronoUnit.DAYS));
            }
            // История обучения не удаляется при деактивации.
            if (s >= 47) student.deactivate();
        }
        // Архивирование происходит после формирования исторической активности.
        courses.get(14).course().archive();
        courses.get(15).course().archive();
        em.flush();
        jdbc.execute("CREATE SEQUENCE courseflow_demo_seed_v1");
        return true;
    }

    private void populateActivity(SeedCourse item, User student, int s, int e, Instant now) {
        int submissions = switch (e) { case 0 -> 0; case 1 -> 2; default -> 3; };
        for (int m = 0; m < submissions; m++) {
            Assignment assignment = item.assignments().get(m);
            DemoContent.ModuleData data = item.data().modules().get(m);
            Instant submitted = earlier(assignment.getDueAt().minus(1 + s % 4, ChronoUnit.HOURS), now.minus(1, ChronoUnit.DAYS));
            int grade = e == 3 ? 90 + (s + m) % 11 : 40 + (s * 7 + m * 13) % 51;
            String answer = grade >= 90
                    ? data.solution() + " Проверка: сравниваю результат с условиями задания и отдельно проверяю граничный случай."
                    : "Предлагаемый подход: " + data.solution();
            Submission submission = save(new Submission(assignment, student, answer, submitted));
            if (e == 3 || (s + m) % 4 != 0) {
                String feedback = grade >= 90 ? "Подход корректен, основные условия учтены."
                        : "Основная идея понятна. Не хватает конкретного примера проверки и подробного обоснования.";
                submission.grade(grade, feedback);
            }
        }
        // 0 + 3 + 4 + 5 = 12 попыток на студента. Не более двух на один Quiz.
        if (e == 0) return;
        for (int m = 0; m < 3; m++) {
            Quiz quiz = item.quizzes().get(m);
            int attempts = m < e - 1 ? 2 : 1;
            Instant taken = earlier(item.course().getStartDate().plusDays(m * 14L + 5)
                    .atTime(16, 0).toInstant(ZoneOffset.UTC), now.minus(2, ChronoUnit.DAYS));
            for (int attempt = 1; attempt <= attempts; attempt++) {
                Instant at = taken.plus(attempt - 1L, ChronoUnit.HOURS);
                QuizSubmission result = save(new QuizSubmission(quiz, student, attempt, at));
                if (e == 2 && m == 2 && s % 7 == 0) {
                    result.expire(result.getExpiresAt());
                    continue;
                }
                int desired = e == 3 && attempt == attempts ? 10 : 3 + (s + m + attempt * 2) % 7;
                int correct = 0;
                List<Question> questions = quiz.getQuestions();
                for (int q = 0; q < questions.size(); q++) {
                    Question question = questions.get(q);
                    boolean right = (q + s) % questions.size() < desired;
                    List<AnswerOption> options = question.getAnswerOptions();
                    if (right) {
                        for (AnswerOption option : options) if (option.isCorrect()) save(result.addSelection(question, option));
                        correct++;
                    } else {
                        AnswerOption wrong = options.stream().filter(option -> !option.isCorrect()).findFirst().orElseThrow();
                        save(result.addSelection(question, wrong));
                    }
                }
                result.complete(100 * correct / questions.size(), at.plus(8 + s % 10, ChronoUnit.MINUTES));
            }
        }
    }

    private static String review(DemoContent.CourseData data, int rating, int student) {
        String topic = data.modules().get(student % 3).title();
        return switch (rating) {
            case 5 -> "Особенно полезен модуль «" + topic + "». Практика помогла связать теорию с реальными задачами.";
            case 4 -> "Материал модуля «" + topic + "» понятен, хотелось бы ещё один разобранный пример.";
            case 3 -> "Модуль «" + topic + "» требует больше самостоятельной подготовки. Не всегда хватало времени на практику.";
            case 2 -> "В разделе «" + topic + "» мне не хватило промежуточных упражнений и пояснений.";
            default -> "Не смог разобраться в теме «" + topic + "» без дополнительных материалов. Хотелось бы более постепенного объяснения.";
        };
    }

    private Category category(Map<String, Category> cache, String name) {
        return cache.computeIfAbsent(name.toLowerCase(Locale.ROOT), key -> {
            List<Long> ids = jdbc.queryForList("SELECT id FROM categories WHERE lower(name) = lower(?)", Long.class, name);
            return ids.isEmpty() ? save(new Category(name, "Учебные курсы направления «" + name + "».")) : em.find(Category.class, ids.get(0));
        });
    }

    private Tag tag(Map<String, Tag> cache, String name) {
        return cache.computeIfAbsent(name.toLowerCase(Locale.ROOT), key -> {
            List<Long> ids = jdbc.queryForList("SELECT id FROM tags WHERE lower(name) = lower(?)", Long.class, name);
            return ids.isEmpty() ? save(new Tag(name)) : em.find(Tag.class, ids.get(0));
        });
    }

    private <T> T save(T entity) {
        em.persist(entity);
        return entity;
    }

    private static Instant earlier(Instant a, Instant b) { return a.isBefore(b) ? a : b; }

    private record SeedCourse(Course course, DemoContent.CourseData data, List<Assignment> assignments, List<Quiz> quizzes) {}
}
