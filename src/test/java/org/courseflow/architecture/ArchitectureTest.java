package org.courseflow.architecture;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.domain.JavaModifier;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import java.nio.file.Files;
import java.nio.file.Path;
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
          Map.entry("assessment", Set.of("catalog", "common", "content", "identity", "learning")),
          Map.entry("auth", Set.of("common", "identity")),
          Map.entry("catalog", Set.of("common", "content", "identity", "learning", "review")),
          Map.entry("common", Set.of()),
          Map.entry("content", Set.of("assessment", "catalog", "common", "identity", "learning")),
          Map.entry(
              "dashboard",
              Set.of("assessment", "catalog", "common", "content", "identity", "learning")),
          Map.entry(
              "demo",
              Set.of("assessment", "auth", "catalog", "content", "identity", "learning", "review")),
          Map.entry("identity", Set.of("auth", "catalog", "common", "learning")),
          Map.entry("learning", Set.of("assessment", "catalog", "common", "content", "identity")),
          Map.entry("review", Set.of("catalog", "common", "identity", "learning")),
          Map.entry("security", Set.of("auth", "common", "identity")));

  // Reviewed class pairs on cyclic application-module directions only (no entity endpoints).
  // Candidate baseline from the reviewed production source snapshot; bytecode is checked below.
  // Deleting an existing dependency is allowed. Remove obsolete entries after review to prevent
  // their reintroduction. Never regenerate this list from a failing test automatically.
  private static final Set<String> REVIEWED_CYCLIC_CLASS_EDGES =
      Set.of(
          "org.courseflow.assessment.service.QuestionService"
              + " -> org.courseflow.catalog.api.CourseAccessPolicy",
          "org.courseflow.assessment.service.QuestionService"
              + " -> org.courseflow.content.api.ContentAccessService",
          "org.courseflow.assessment.service.QuizAttemptQueryService"
              + " -> org.courseflow.catalog.api.CourseAccessPolicy",
          "org.courseflow.assessment.service.QuizAttemptQueryService"
              + " -> org.courseflow.catalog.api.CourseGuard",
          "org.courseflow.assessment.service.QuizAttemptService"
              + " -> org.courseflow.catalog.api.CourseAccessPolicy",
          "org.courseflow.assessment.service.QuizAttemptService"
              + " -> org.courseflow.catalog.api.CourseGuard",
          "org.courseflow.assessment.service.QuizAttemptService"
              + " -> org.courseflow.identity.api.StudentAccess",
          "org.courseflow.assessment.service.QuizAttemptService"
              + " -> org.courseflow.learning.api.EnrollmentAccess",
          "org.courseflow.assessment.service.QuizService"
              + " -> org.courseflow.catalog.api.CourseAccessPolicy",
          "org.courseflow.assessment.service.QuizService -> org.courseflow.catalog.api.CourseGuard",
          "org.courseflow.assessment.service.QuizService"
              + " -> org.courseflow.content.api.ContentAccessService",
          "org.courseflow.assessment.service.QuizService"
              + " -> org.courseflow.content.api.ContentStructure",
          "org.courseflow.auth.api.AccountCreator -> org.courseflow.identity.api.UserDirectory",
          "org.courseflow.auth.service.AdminPasswordResetService"
              + " -> org.courseflow.identity.api.UserDirectory",
          "org.courseflow.auth.service.SystemAdminBootstrapService"
              + " -> org.courseflow.identity.api.UserDirectory",
          "org.courseflow.catalog.service.CourseService"
              + " -> org.courseflow.content.api.ContentStructure",
          "org.courseflow.catalog.service.CourseService"
              + " -> org.courseflow.content.api.LessonScheduleChecks",
          "org.courseflow.catalog.service.CourseService"
              + " -> org.courseflow.identity.api.UserDirectory",
          "org.courseflow.catalog.service.CourseService"
              + " -> org.courseflow.learning.api.EnrollmentAccess",
          "org.courseflow.catalog.service.CourseService -> org.courseflow.review.api.ReviewAccess",
          "org.courseflow.content.api.ContentAccessService"
              + " -> org.courseflow.catalog.api.CourseGuard",
          "org.courseflow.content.api.ContentAccessService"
              + " -> org.courseflow.learning.api.EnrollmentAccess",
          "org.courseflow.content.service.LessonService -> org.courseflow.catalog.api.CourseGuard",
          "org.courseflow.content.service.LessonService"
              + " -> org.courseflow.learning.api.AssignmentAccess",
          "org.courseflow.content.service.LessonSessionService"
              + " -> org.courseflow.catalog.api.CourseGuard",
          "org.courseflow.content.service.LessonSessionService"
              + " -> org.courseflow.identity.api.UserDirectory",
          "org.courseflow.content.service.ModuleService"
              + " -> org.courseflow.assessment.api.QuizAccess",
          "org.courseflow.content.service.ModuleService -> org.courseflow.catalog.api.CourseGuard",
          "org.courseflow.identity.service.AdminUserService"
              + " -> org.courseflow.auth.api.AccountCreator",
          "org.courseflow.identity.service.AdminUserService"
              + " -> org.courseflow.auth.api.CredentialLifecycle",
          "org.courseflow.identity.service.AdminUserService"
              + " -> org.courseflow.catalog.api.CourseParticipation",
          "org.courseflow.identity.service.AdminUserService"
              + " -> org.courseflow.learning.api.EnrollmentAccess",
          "org.courseflow.learning.service.AssignmentService"
              + " -> org.courseflow.catalog.api.CourseAccessPolicy",
          "org.courseflow.learning.service.AssignmentService"
              + " -> org.courseflow.catalog.api.CourseGuard",
          "org.courseflow.learning.service.AssignmentService"
              + " -> org.courseflow.content.api.ContentAccessService",
          "org.courseflow.learning.service.AssignmentService"
              + " -> org.courseflow.content.api.ContentStructure",
          "org.courseflow.learning.service.EnrollmentService"
              + " -> org.courseflow.catalog.api.CourseAccessPolicy",
          "org.courseflow.learning.service.EnrollmentService"
              + " -> org.courseflow.catalog.api.CourseGuard",
          "org.courseflow.learning.service.EnrollmentService"
              + " -> org.courseflow.identity.api.StudentAccess",
          "org.courseflow.learning.service.GradebookService"
              + " -> org.courseflow.catalog.api.CourseAccessPolicy",
          "org.courseflow.learning.service.GradebookService"
              + " -> org.courseflow.catalog.api.CourseGuard",
          "org.courseflow.learning.service.GradebookService"
              + " -> org.courseflow.content.api.ContentStructure",
          "org.courseflow.learning.service.SubmissionService"
              + " -> org.courseflow.catalog.api.CourseAccessPolicy",
          "org.courseflow.learning.service.SubmissionService"
              + " -> org.courseflow.catalog.api.CourseGuard",
          "org.courseflow.learning.service.SubmissionService"
              + " -> org.courseflow.identity.api.StudentAccess",
          "org.courseflow.review.service.CourseReviewService"
              + " -> org.courseflow.catalog.api.CourseAccessPolicy",
          "org.courseflow.review.service.CourseReviewService"
              + " -> org.courseflow.catalog.api.CourseGuard",
          "org.courseflow.review.service.CourseReviewService"
              + " -> org.courseflow.identity.api.StudentAccess",
          "org.courseflow.review.service.CourseReviewService"
              + " -> org.courseflow.identity.api.UserDirectory",
          "org.courseflow.review.service.CourseReviewService"
              + " -> org.courseflow.learning.api.EnrollmentAccess");

  private static JavaClasses classes;

  @BeforeAll
  static void importProductionClasses() {
    classes =
        new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages(ROOT);
  }

  @Test
  void everyCompiledProductionClassIsImported() throws Exception {
    Path root = Path.of(org.courseflow.CourseFlowApplication.class.getProtectionDomain()
        .getCodeSource().getLocation().toURI());
    Path packageRoot = root.resolve("org/courseflow");
    assertTrue(Files.isDirectory(packageRoot), "Expected compiled production classes: " + packageRoot);
    Set<String> expected = new TreeSet<>();
    try (var paths = Files.walk(packageRoot)) {
      for (Path file : paths.filter(p -> p.toString().endsWith(".class")).toList()) {
        String name = root.relativize(file).toString().replace('\\', '/').replace('/', '.');
        if (!name.endsWith("package-info.class")) {
          expected.add(name.substring(0, name.length() - 6));
        }
      }
    }
    Set<String> imported = new TreeSet<>();
    for (JavaClass type : classes) {
      if (isProject(type)) {
        imported.add(type.getName());
      }
    }
    assertFalse(expected.isEmpty(), "No compiled classes found");
    expected.removeAll(imported);
    assertTrue(expected.isEmpty(), () -> "Production classes not imported: " + expected);
  }

  @Test
  void cycleDetectorRecognizesCyclesAndIgnoresAcyclicEdges() {
    assertEquals(Set.of("a -> b", "b -> a"), cyclicEdges(Map.of("a", Set.of("b"), "b", Set.of("a", "c"))));
    assertTrue(cyclicEdges(Map.of("a", Set.of("b"), "b", Set.of("c"))).isEmpty());
  }

  @Test
  void classCycleBaselineAllowsRemovalButRejectsNewCyclicPairs() {
    String reviewed = "a.First -> b.First";
    String reverse = "b.First -> a.First";
    String added = "a.Second -> b.First";
    String acyclic = "b.First -> c.First";
    Set<String> baseline = Set.of(reviewed, reverse);
    Map<String, Set<String>> graph = Map.of("a", Set.of("b"), "b", Set.of("a", "c"));

    assertEquals(
        Set.of(added),
        unreviewedCyclicClassEdges(
            graph,
            Map.of(
                "a -> b", Set.of(reviewed, added),
                "b -> a", Set.of(reverse),
                "b -> c", Set.of(acyclic)),
            baseline));
    assertTrue(
        unreviewedCyclicClassEdges(
                graph,
                Map.of("a -> b", Set.of(reviewed), "b -> a", Set.of(reverse),
                    "b -> c", Set.of(acyclic)),
                Set.of(reviewed, reverse, added))
            .isEmpty());
    assertTrue(
        unreviewedCyclicClassEdges(
                Map.of("a", Set.of("b")), Map.of("a -> b", Set.of(reviewed)), baseline)
            .isEmpty());
    assertEquals(
        Set.of(reviewed, reverse),
        unreviewedCyclicClassEdges(
            graph, Map.of("a -> b", Set.of(reviewed), "b -> a", Set.of(reverse)), Set.of()));
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
    for (JavaClass type : classes) {
      for (var method : type.getMethods()) {
        for (String annotation : injectionAnnotations) {
          if (method.isAnnotatedWith(annotation)) {
            violations.add(method.getFullName() + " uses method injection @" + annotation);
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
  void internalServicesArePrivateToTheirOwningModule() {
    checkDependencies(
        ArchitectureTest::isProject,
        (origin, target) ->
            isProject(target)
                && layer(target, "service")
                && !module(origin).equals(module(target)));
  }

  @Test
  void moduleDependencyDirectionsAndApplicationCyclesDoNotGrow() throws Exception {
    Map<String, Set<String>> applicationGraph = new HashMap<>();
    Map<String, Set<String>> fullGraph = new HashMap<>();
    Set<String> classEdges = new TreeSet<>();
    Map<String, Set<String>> applicationClassEdges = new HashMap<>();
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
        fullGraph.computeIfAbsent(from, ignored -> new HashSet<>()).add(to);
        String classEdge = type.getName() + " -> " + target.getName();
        classEdges.add(classEdge);
        if (!layer(type, "entity") && !layer(target, "entity")) {
          applicationGraph.computeIfAbsent(from, ignored -> new HashSet<>()).add(to);
          applicationClassEdges
              .computeIfAbsent(from + " -> " + to, ignored -> new TreeSet<>())
              .add(classEdge);
        }
        if (!MODULE_DEPENDENCIES.getOrDefault(from, Set.of()).contains(to)) {
          violations.add(dependency.getDescription());
        }
      }
    }
    Path report = Path.of("build", "reports", "architecture", "bytecode-dependencies.txt");
    Files.createDirectories(report.getParent());
    Files.write(report, classEdges);
    Set<String> actualCyclicClassEdges = cyclicClassEdges(applicationGraph, applicationClassEdges);
    Files.write(report.resolveSibling("cyclic-application-dependencies.txt"), actualCyclicClassEdges);
    System.out.println("Existing application cycle debt: " + cyclicEdges(applicationGraph));
    System.out.println("Cyclic application class pairs: " + actualCyclicClassEdges.size());
    System.out.println("All module cycles, including ORM: " + cyclicEdges(fullGraph));
    Set<String> unreviewed =
        unreviewedCyclicClassEdges(
            applicationGraph, applicationClassEdges, REVIEWED_CYCLIC_CLASS_EDGES);
    assertAll(
        () -> assertNoViolations(violations),
        () ->
            assertTrue(
                unreviewed.isEmpty(),
                () -> "New cyclic application class dependencies:\n" + String.join("\n", unreviewed)));
  }

  private static Set<String> cyclicClassEdges(
      Map<String, Set<String>> graph, Map<String, Set<String>> classEdgesByDirection) {
    Set<String> result = new TreeSet<>();
    for (String direction : cyclicEdges(graph)) {
      result.addAll(classEdgesByDirection.getOrDefault(direction, Set.of()));
    }
    return result;
  }

  private static Set<String> unreviewedCyclicClassEdges(
      Map<String, Set<String>> graph,
      Map<String, Set<String>> classEdgesByDirection,
      Set<String> reviewed) {
    Set<String> result = cyclicClassEdges(graph, classEdgesByDirection);
    result.removeAll(reviewed);
    return result;
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
          if (!isProject(parameter)) {
            continue;
          }
          if (isManagedType(parameter)) {
            graph
                .computeIfAbsent(type.getName(), ignored -> new HashSet<>())
                .add(parameter.getName());
          } else if (parameter.isInterface()) {
            // A public interface (e.g. ProgressQuery) can hide a managed implementation.
            for (JavaClass candidate : classes) {
              if (!candidate.isInterface()
                  && isManagedType(candidate)
                  && candidate.isAssignableTo(parameter.getName())) {
                graph
                    .computeIfAbsent(type.getName(), ignored -> new HashSet<>())
                    .add(candidate.getName());
              }
            }
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
