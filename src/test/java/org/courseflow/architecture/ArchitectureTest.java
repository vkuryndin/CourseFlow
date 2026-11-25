package org.courseflow.architecture;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.domain.JavaModifier;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.BiPredicate;
import java.util.function.Predicate;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** Architecture regression checks for the modular monolith, without a Spring context. */
class ArchitectureTest {
  private static final String ROOT = "org.courseflow";
  private static final String APPLICATION = "application";
  private static final String ENTITY = "jakarta.persistence.Entity";
  private static final String SERVICE = "org.springframework.stereotype.Service";
  private static final String REPOSITORY = "org.springframework.stereotype.Repository";
  private static final String DATA_REPOSITORY = "org.springframework.data.repository.Repository";
  private static final String COMPONENT = "org.springframework.stereotype.Component";
  private static final String CONFIGURATION =
      "org.springframework.context.annotation.Configuration";
  private static final String REST_CONTROLLER =
      "org.springframework.web.bind.annotation.RestController";
  private static final String CONTROLLER = "org.springframework.stereotype.Controller";
  private static final String REST_ADVICE =
      "org.springframework.web.bind.annotation.RestControllerAdvice";
  private static final String ADVICE = "org.springframework.web.bind.annotation.ControllerAdvice";

  // Reviewed existing directions, including bidirectional ORM and application-level collaboration.
  // This is an explicit policy, not a baseline learned automatically from the classes being tested.
  private static final Map<String, Set<String>> MODULE_DEPENDENCIES =
      Map.ofEntries(
          Map.entry(APPLICATION, Set.of()),
          Map.entry(
              "assessment",
              Set.of("catalog", "common", "content", "identity", "learning", "security")),
          Map.entry("auth", Set.of("common", "identity", "security")),
          Map.entry(
              "catalog", Set.of("common", "content", "identity", "learning", "review", "security")),
          Map.entry("common", Set.of("security")),
          Map.entry(
              "content",
              Set.of("assessment", "catalog", "common", "identity", "learning", "security")),
          Map.entry(
              "dashboard",
              Set.of(
                  "assessment",
                  "catalog",
                  "common",
                  "content",
                  "identity",
                  "learning",
                  "security")),
          Map.entry(
              "demo",
              Set.of("assessment", "auth", "catalog", "content", "identity", "learning", "review")),
          Map.entry("identity", Set.of("auth", "catalog", "common", "learning", "security")),
          Map.entry(
              "learning",
              Set.of("assessment", "catalog", "common", "content", "identity", "security")),
          Map.entry("review", Set.of("catalog", "common", "identity", "learning", "security")),
          Map.entry("security", Set.of("auth", "common", "identity")));

  // Existing calls to another business module's service layer. New collaboration should use
  // that module's explicit API or be reviewed before adding a new exception here.
  private static final Set<String> CROSS_MODULE_SERVICE_DEPENDENCIES =
      Set.of(
          "assessment.service.QuestionService -> catalog.service.CourseAccessPolicy",
          "assessment.service.QuestionService -> content.service.ContentAccessService",
          "assessment.service.QuizAttemptQueryService -> catalog.service.CourseAccessPolicy",
          "assessment.service.QuizAttemptQueryService -> catalog.service.CourseGuard",
          "assessment.service.QuizAttemptService -> catalog.service.CourseAccessPolicy",
          "assessment.service.QuizAttemptService -> catalog.service.CourseGuard",
          "assessment.service.QuizAttemptService -> identity.service.StudentAccess",
          "assessment.service.QuizAttemptService -> learning.service.EnrollmentAccess",
          "assessment.service.QuizService -> catalog.service.CourseAccessPolicy",
          "assessment.service.QuizService -> catalog.service.CourseGuard",
          "assessment.service.QuizService -> content.service.ContentAccessService",
          "catalog.service.CourseService -> content.service.LessonScheduleChecks",
          "catalog.service.CourseService -> learning.service.EnrollmentAccess",
          "content.service.ContentAccessService -> catalog.service.CourseGuard",
          "content.service.ContentAccessService -> learning.service.EnrollmentAccess",
          "content.service.LessonService -> catalog.service.CourseGuard",
          "content.service.LessonSessionService -> catalog.service.CourseGuard",
          "content.service.ModuleService -> catalog.service.CourseGuard",
          "dashboard.service.StudentDashboardService -> learning.service.ProgressService",
          "identity.service.AdminUserService -> auth.service.AccountCreator",
          "identity.service.AdminUserService -> auth.service.CredentialLifecycle",
          "identity.service.AdminUserService -> catalog.service.CourseParticipation",
          "identity.service.AdminUserService -> learning.service.EnrollmentAccess",
          "learning.service.AssignmentService -> catalog.service.CourseAccessPolicy",
          "learning.service.AssignmentService -> catalog.service.CourseGuard",
          "learning.service.AssignmentService -> content.service.ContentAccessService",
          "learning.service.EnrollmentService -> catalog.service.CourseAccessPolicy",
          "learning.service.EnrollmentService -> catalog.service.CourseGuard",
          "learning.service.EnrollmentService -> identity.service.StudentAccess",
          "learning.service.GradebookService -> catalog.service.CourseAccessPolicy",
          "learning.service.GradebookService -> catalog.service.CourseGuard",
          "learning.service.SubmissionService -> catalog.service.CourseAccessPolicy",
          "learning.service.SubmissionService -> catalog.service.CourseGuard",
          "learning.service.SubmissionService -> identity.service.StudentAccess",
          "review.service.CourseReviewService -> catalog.service.CourseAccessPolicy",
          "review.service.CourseReviewService -> catalog.service.CourseGuard",
          "review.service.CourseReviewService -> identity.service.StudentAccess",
          "review.service.CourseReviewService -> learning.service.EnrollmentAccess");

  private static JavaClasses classes;

  @BeforeAll
  static void importProductionClasses() {
    classes =
        new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages(ROOT);
  }

  @Test
  void allProductionModulesAreImportedAndKnown() {
    Set<String> actual = new TreeSet<>();
    List<String> violations = new ArrayList<>();
    for (JavaClass type : classes) {
      if (isProject(type)) {
        actual.add(module(type));
        if (type.getPackageName().equals(ROOT)
            && !type.getName().equals(ROOT + ".CourseFlowApplication")) {
          violations.add("Unexpected root class: " + type.getName());
        }
      }
    }
    assertAll(
        () ->
            assertTrue(
                actual.equals(MODULE_DEPENDENCIES.keySet()),
                () ->
                    "Expected modules "
                        + new TreeSet<>(MODULE_DEPENDENCIES.keySet())
                        + "; imported "
                        + actual),
        () -> assertNoViolations(violations));
  }

  @Test
  void controllersCannotAccessPersistenceDirectly() {
    checkDependencies(
        ArchitectureTest::isWebBoundary,
        (origin, target) ->
            isRepository(target)
                || (isPersistenceType(target) && !isAllowedPersistenceException(origin, target)));
  }

  @Test
  void nonWebCodeCannotDependOnControllers() {
    checkDependencies(type -> !isWebBoundary(type), (origin, target) -> isWebBoundary(target));
  }

  @Test
  void repositoriesCannotDependOnHigherLayers() {
    checkDependencies(
        ArchitectureTest::isRepository,
        (origin, target) ->
            isWebBoundary(target)
                || isBusinessService(target)
                || (isProject(target) && layer(target, "api") && !module(target).equals("common"))
                || (isProject(target) && module(target).equals("security"))
                || isWebFrameworkType(target));
  }

  @Test
  void repositoriesArePrivateToTheirOwningModule() {
    checkDependencies(
        ArchitectureTest::isProject,
        (origin, target) ->
            isProject(target) && isRepository(target) && !module(origin).equals(module(target)));
  }

  @Test
  void businessServicesAndModuleApisAreIndependentOfHttp() {
    // common.system and security are infrastructure, not business services.
    checkDependencies(
        type -> layer(type, "service") || (layer(type, "api") && !module(type).equals("common")),
        (origin, target) -> isWebBoundary(target) || isWebFrameworkType(target));
  }

  @Test
  void domainModelDoesNotDependOnApplicationOrInfrastructureLayers() {
    checkDependencies(
        type -> layer(type, "entity") || type.isAnnotatedWith(ENTITY),
        (origin, target) ->
            (isProject(target) && !layer(target, "entity"))
                || inPackage(target, "org.springframework")
                || inPackage(target, "jakarta.servlet")
                || inPackage(target, "javax.servlet")
                || inPackage(target, "java.sql")
                || inPackage(target, "javax.sql")
                || inPackage(target, "com.fasterxml.jackson"));
  }

  @Test
  void dtosCannotDependOnPersistenceOrBusinessServices() {
    checkDependencies(
        ArchitectureTest::isDto,
        (origin, target) ->
            isRepository(target)
                || isPersistenceType(target)
                || isWebBoundary(target)
                || isBusinessService(target)
                || (isProject(target) && layer(target, "api") && !module(target).equals("common"))
                || (isProject(target) && module(target).equals("security")));
  }

  @Test
  void controllersDoNotReturnJpaEntitiesEvenInsideGenericContainers() {
    List<String> violations = new ArrayList<>();
    for (JavaClass type : classes) {
      if (!isController(type) && !isAdvice(type)) {
        continue;
      }
      for (var method : type.getMethods()) {
        if (!method.getModifiers().contains(JavaModifier.PUBLIC)) {
          continue;
        }
        for (JavaClass returned : method.getReturnType().getAllInvolvedRawTypes()) {
          if (returned.isAnnotatedWith(ENTITY)) {
            violations.add(method.getFullName() + " exposes entity " + returned.getName());
          }
        }
      }
    }
    assertNoViolations(violations);
  }

  @Test
  void dtosDoNotStoreJpaEntitiesEvenInsideGenericContainers() {
    List<String> violations = new ArrayList<>();
    for (JavaClass type : classes) {
      if (!isDto(type)) {
        continue;
      }
      for (var field : type.getFields()) {
        for (JavaClass fieldType : field.getType().getAllInvolvedRawTypes()) {
          if (fieldType.isAnnotatedWith(ENTITY)) {
            violations.add(field.getFullName() + " stores entity " + fieldType.getName());
          }
        }
      }
    }
    assertNoViolations(violations);
  }

  @Test
  void springDependenciesAreNotInjectedIntoFields() {
    Set<String> injectionAnnotations =
        Set.of(
            "org.springframework.beans.factory.annotation.Autowired",
            "jakarta.inject.Inject",
            "javax.inject.Inject",
            "jakarta.annotation.Resource",
            "javax.annotation.Resource",
            "jakarta.persistence.PersistenceContext",
            "javax.persistence.PersistenceContext",
            "jakarta.persistence.PersistenceUnit",
            "javax.persistence.PersistenceUnit");
    List<String> violations = new ArrayList<>();
    for (JavaClass type : classes) {
      for (var field : type.getFields()) {
        for (String annotation : injectionAnnotations) {
          if (field.isAnnotatedWith(annotation)) {
            violations.add(field.getFullName() + " uses field injection @" + annotation);
          }
        }
      }
    }
    // @Value is deliberately not included: configuration values are not bean dependencies.
    assertNoViolations(violations);
  }

  @Test
  void frameworkStereotypesResideInTheAppropriateLayers() {
    List<String> violations = new ArrayList<>();
    for (JavaClass type : classes) {
      if (type.isAnnotatedWith(ENTITY) && !layer(type, "entity")) {
        violations.add(type.getName() + " must reside in an entity package");
      }
      if (isController(type) && !layer(type, "controller") && !isSystem(type)) {
        violations.add(type.getName() + " must reside in a controller or common.system package");
      }
      if (isAdvice(type) && !inPackage(type, ROOT + ".common.exception")) {
        violations.add(type.getName() + " must reside in common.exception");
      }
      if ((type.isAnnotatedWith(REPOSITORY) || type.isAssignableTo(DATA_REPOSITORY))
          && !layer(type, "repository")) {
        violations.add(type.getName() + " must reside in a repository package");
      }
      if (type.isAnnotatedWith(SERVICE)
          && !layer(type, "service")
          && !(layer(type, "api") && !module(type).equals("common"))
          && !isSystem(type)
          && !module(type).equals("security")
          && !module(type).equals("demo")) {
        violations.add(
            type.getName() + " must reside in a service or permitted infrastructure/API package");
      }
    }
    assertNoViolations(violations);
  }

  @Test
  void crossModuleServiceDependenciesDoNotGrowWithoutReview() {
    checkDependencies(
        ArchitectureTest::isProject,
        (origin, target) ->
            isProject(target)
                && layer(target, "service")
                && !module(origin).equals(module(target))
                && !CROSS_MODULE_SERVICE_DEPENDENCIES.contains(
                    shortName(origin) + " -> " + shortName(target)));
  }

  @Test
  void moduleDependencyDirectionsAndExistingCyclesDoNotGrow() {
    Map<String, Set<String>> actual = new HashMap<>();
    List<String> violations = new ArrayList<>();
    for (JavaClass type : classes) {
      if (!isProject(type)) {
        continue;
      }
      for (var dependency : type.getDirectDependenciesFromSelf()) {
        JavaClass target = dependency.getTargetClass();
        if (!isProject(target) || module(type).equals(module(target))) {
          continue;
        }
        String from = module(type);
        String to = module(target);
        actual.computeIfAbsent(from, ignored -> new HashSet<>()).add(to);
        if (!MODULE_DEPENDENCIES.getOrDefault(from, Set.of()).contains(to)) {
          violations.add(dependency.getDescription());
        }
      }
    }
    Set<String> newCyclicEdges = cyclicEdges(actual);
    newCyclicEdges.removeAll(cyclicEdges(MODULE_DEPENDENCIES));
    assertAll(
        () -> assertNoViolations(violations),
        () ->
            assertTrue(
                newCyclicEdges.isEmpty(), () -> "New cyclic module edges: " + newCyclicEdges));
  }

  @Test
  void springComponentConstructorDependenciesHaveNoCycles() {
    Map<String, Set<String>> graph = new HashMap<>();
    for (JavaClass type : classes) {
      if (!isManagedType(type)) {
        continue;
      }
      for (var constructor : type.getConstructors()) {
        for (JavaClass parameter : constructor.getRawParameterTypes()) {
          if (isProject(parameter) && isManagedType(parameter)) {
            graph
                .computeIfAbsent(type.getName(), ignored -> new HashSet<>())
                .add(parameter.getName());
          }
        }
      }
    }
    Set<String> cycles = cyclicEdges(graph);
    assertTrue(cycles.isEmpty(), () -> "Cyclic constructor dependencies: " + cycles);
  }

  private static boolean isProject(JavaClass type) {
    return inPackage(type, ROOT);
  }

  private static boolean inPackage(JavaClass type, String name) {
    String pkg = type.getPackageName();
    return pkg.equals(name) || pkg.startsWith(name + ".");
  }

  private static String module(JavaClass type) {
    String pkg = type.getPackageName();
    if (!isProject(type) || pkg.equals(ROOT)) {
      return APPLICATION;
    }
    return pkg.substring(ROOT.length() + 1).split("\\.", 2)[0];
  }

  private static boolean layer(JavaClass type, String name) {
    return isProject(type) && inPackage(type, ROOT + "." + module(type) + "." + name);
  }

  private static boolean isSystem(JavaClass type) {
    return inPackage(type, ROOT + ".common.system");
  }

  private static boolean isController(JavaClass type) {
    return type.isAnnotatedWith(REST_CONTROLLER) || type.isAnnotatedWith(CONTROLLER);
  }

  private static boolean isAdvice(JavaClass type) {
    return type.isAnnotatedWith(REST_ADVICE) || type.isAnnotatedWith(ADVICE);
  }

  private static boolean isWebBoundary(JavaClass type) {
    return isProject(type) && (layer(type, "controller") || isController(type) || isAdvice(type));
  }

  private static boolean isRepository(JavaClass type) {
    return layer(type, "repository")
        || type.isAnnotatedWith(REPOSITORY)
        || type.isAssignableTo(DATA_REPOSITORY);
  }

  private static boolean isBusinessService(JavaClass type) {
    return isProject(type) && (layer(type, "service") || type.isAnnotatedWith(SERVICE));
  }

  private static boolean isDto(JavaClass type) {
    return layer(type, "dto")
        || type.getName().equals(ROOT + ".common.api.PageResponse")
        || type.getName().equals(ROOT + ".common.exception.ApiErrorResponse")
        || (isSystem(type) && type.getName().split("\\$", 2)[0].endsWith("Response"));
  }

  private static boolean isWebFrameworkType(JavaClass type) {
    return inPackage(type, "org.springframework.web")
        || inPackage(type, "org.springframework.http")
        || inPackage(type, "jakarta.servlet")
        || inPackage(type, "javax.servlet");
  }

  private static boolean isAllowedPersistenceException(JavaClass origin, JavaClass target) {
    // The global HTTP error handler translates these JPA locking failures into HTTP 409.
    // Referring to exception types is not a direct persistence operation.
    return isAdvice(origin)
        && origin.getName().equals(ROOT + ".common.exception.GlobalExceptionHandler")
        && Set.of(
                "jakarta.persistence.LockTimeoutException",
                "jakarta.persistence.OptimisticLockException",
                "jakarta.persistence.PessimisticLockException")
            .contains(target.getName());
  }

  private static boolean isPersistenceType(JavaClass type) {
    return inPackage(type, "jakarta.persistence")
        || inPackage(type, "javax.persistence")
        || inPackage(type, "org.hibernate")
        || inPackage(type, "org.springframework.data.jpa")
        || inPackage(type, "org.springframework.data.repository")
        || inPackage(type, "org.springframework.jdbc")
        || inPackage(type, "java.sql")
        || inPackage(type, "javax.sql")
        || type.getName().equals(ROOT + ".common.api.JpqlPages");
  }

  private static boolean isManagedType(JavaClass type) {
    return type.isAnnotatedWith(COMPONENT)
        || type.isAnnotatedWith(SERVICE)
        || type.isAnnotatedWith(CONFIGURATION)
        || isController(type)
        || isAdvice(type)
        || type.isAnnotatedWith(REPOSITORY)
        || type.isAssignableTo(DATA_REPOSITORY);
  }

  private static String shortName(JavaClass type) {
    return type.getName().substring(ROOT.length() + 1);
  }

  private static void checkDependencies(
      Predicate<JavaClass> select, BiPredicate<JavaClass, JavaClass> forbidden) {
    List<String> violations = new ArrayList<>();
    for (JavaClass origin : classes) {
      if (!select.test(origin)) {
        continue;
      }
      for (var dependency : origin.getDirectDependenciesFromSelf()) {
        if (forbidden.test(origin, dependency.getTargetClass())) {
          violations.add(dependency.getDescription());
        }
      }
    }
    assertNoViolations(violations);
  }

  private static void assertNoViolations(List<String> violations) {
    assertTrue(violations.isEmpty(), () -> String.join("\n", new TreeSet<>(violations)));
  }

  private static Set<String> cyclicEdges(Map<String, Set<String>> graph) {
    Set<String> result = new TreeSet<>();
    graph.forEach(
        (from, targets) -> {
          for (String target : targets) {
            if (reachable(graph, target, from)) {
              result.add(from + " -> " + target);
            }
          }
        });
    return result;
  }

  private static boolean reachable(Map<String, Set<String>> graph, String start, String goal) {
    Set<String> visited = new HashSet<>();
    ArrayDeque<String> pending = new ArrayDeque<>();
    pending.add(start);
    while (!pending.isEmpty()) {
      String current = pending.removeFirst();
      if (current.equals(goal)) {
        return true;
      }
      if (visited.add(current)) {
        pending.addAll(graph.getOrDefault(current, Set.of()));
      }
    }
    return false;
  }
}
