package org.courseflow.demo;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import org.courseflow.identity.entity.UserRole;

/** Все имена вымышлены, адреса используют зарезервированный домен .invalid. */
public final class DemoAccounts {
  private DemoAccounts() {}

  public record Account(
      String name, String email, String password, Set<UserRole> roles, String bio) {
    @Override
    public String toString() {
      return "Account[email=" + email + ", roles=" + roles + ", password=[REDACTED]]";
    }
  }

  private static final String[] TEACHERS = {
    "Александр Власов", "Елена Морозова", "Мария Соколова", "Дмитрий Волков",
    "Илья Новиков", "Анна Орлова", "Сергей Лебедев", "Ольга Белова"
  };
  private static final String[] EXPERTISE = {
    "Java и Python, разработка прикладных программ", "Spring, ORM и проектирование API",
    "React, TypeScript и доступность интерфейсов", "SQL, PostgreSQL и оптимизация данных",
    "Контейнеризация и эксплуатация приложений", "Защита сетей и веб-приложений",
    "Машинное обучение и языковые модели", "Продуктовая разработка и гибкие процессы"
  };
  private static final String[] FIRST = {
    "Артём", "Виктория", "Никита", "Дарья", "Михаил", "Полина", "Андрей", "Софья", "Максим", "Алина"
  };
  private static final String[] LAST_M = {"Кузнецов", "Попов", "Смирнов", "Иванов", "Фёдоров"};
  private static final String[] LAST_F = {"Кузнецова", "Попова", "Смирнова", "Иванова", "Фёдорова"};
  private static final String[] INTERESTS = {
    "разработка приложений",
    "аналитика данных",
    "веб-интерфейсы",
    "инфраструктура",
    "управление продуктами"
  };

  public static List<Account> create() {
    SecureRandom random = new SecureRandom();
    String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 12);
    List<Account> accounts = new ArrayList<>();
    accounts.add(
        account(
            random,
            suffix,
            "admin",
            1,
            "Ирина Павлова",
            Set.of(UserRole.ADMIN),
            "Координирует учебный процесс академии."));
    accounts.add(
        account(
            random,
            suffix,
            "admin",
            2,
            "Павел Громов",
            Set.of(UserRole.ADMIN),
            "Администрирует каталог и учётные записи."));
    for (int i = 0; i < TEACHERS.length; i++) {
      Set<UserRole> roles =
          i == 0 || i == 4 ? Set.of(UserRole.TEACHER, UserRole.STUDENT) : Set.of(UserRole.TEACHER);
      accounts.add(
          account(
              random,
              suffix,
              "teacher",
              i + 1,
              TEACHERS[i],
              roles,
              "Преподаватель. Специализация: "
                  + EXPERTISE[i]
                  + ". Ведёт практические занятия и проверяет работы."));
    }
    for (int i = 0; i < 50; i++) {
      String[] surnames = i % 2 == 0 ? LAST_M : LAST_F;
      String name = FIRST[i % 10] + " " + surnames[i / 10];
      accounts.add(
          account(
              random,
              suffix,
              "student",
              i + 1,
              name,
              Set.of(UserRole.STUDENT),
              "Студент академии. Интересы: " + INTERESTS[(i / 2) % INTERESTS.length] + "."));
    }
    return List.copyOf(accounts);
  }

  private static Account account(
      SecureRandom random,
      String suffix,
      String prefix,
      int number,
      String name,
      Set<UserRole> roles,
      String bio) {
    byte[] bytes = new byte[24];
    random.nextBytes(bytes);
    String password = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    String email =
        String.format(Locale.ROOT, "%s%02d.%s@demo.courseflow.invalid", prefix, number, suffix);
    return new Account(name, email, password, roles, bio);
  }
}
