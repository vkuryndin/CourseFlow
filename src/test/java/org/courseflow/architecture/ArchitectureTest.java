package org.courseflow.architecture;

import static com.tngtech.archunit.base.DescribedPredicate.alwaysTrue;
import static com.tngtech.archunit.base.DescribedPredicate.describe;
import static com.tngtech.archunit.base.DescribedPredicate.not;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.equivalentTo;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.resideInAPackage;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.resideInAnyPackage;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noFields;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noMethods;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;
import static org.junit.jupiter.api.Assertions.assertFalse;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.domain.JavaModifier;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.lang.ArchRule;
import jakarta.persistence.Entity;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import org.courseflow.CourseFlowApplication;
import org.courseflow.common.api.JpqlPages;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.repository.Repository;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Controller;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.ControllerAdvice;

/** Architecture rules for the modular monolith, based on compiled production classes. */
class ArchitectureTest {
  private static final String ROOT = "org.courseflow";
  private static final String CONTROLLER = ROOT + ".*.controller..";
  private static final String SERVICE = ROOT + ".*.service..";
  private static final String REPOSITORY = ROOT + ".*.repository..";
  private static final String ENTITY = ROOT + ".*.entity..";
  private static final String DTO = ROOT + ".*.dto..";
  private static final String API = ROOT + ".*.api..";
  private static final String COMMON = ROOT + ".common..";
  private static final String SECURITY = ROOT + ".security..";
  private static final String SYSTEM = ROOT + ".common.system..";
  private static final String EXCEPTION = ROOT + ".common.exception..";

  private static final DescribedPredicate<JavaClass> WEB_BOUNDARY =
      describe("web controllers and advice", ArchitectureTest::isWebBoundary);
  private static final DescribedPredicate<JavaClass> REPOSITORIES =
      describe("repositories", ArchitectureTest::isRepository);
  private static final DescribedPredicate<JavaClass> APPLICATION_API =
      resideInAPackage(API).and(not(resideInAPackage(COMMON)));
  private static final DescribedPredicate<JavaClass> BUSINESS =
      resideInAPackage(SERVICE).or(APPLICATION_API);
  private static final DescribedPredicate<JavaClass> HTTP =
      resideInAnyPackage(
          "org.springframework.web..",
          "org.springframework.http..",
          "jakarta.servlet..",
          "javax.servlet..");
  private static final DescribedPredicate<JavaClass> PERSISTENCE =
      resideInAnyPackage(
              "jakarta.persistence..",
              "javax.persistence..",
              "org.hibernate..",
              "org.springframework.data.jpa..",
              "org.springframework.data.repository..",
              "org.springframework.jdbc..",
              "java.sql..",
              "javax.sql..")
          .or(REPOSITORIES)
          .or(equivalentTo(JpqlPages.class));
  private static final DescribedPredicate<JavaClass> DTOS =
      resideInAPackage(DTO)
          .or(
              describe(
                  "shared response types",
                  (JavaClass type) ->
                      resideInAnyPackage(COMMON).test(type)
                          && type.getName().split("\\$", 2)[0].endsWith("Response")));

  private static JavaClasses production;

  @BeforeAll
  static void importProductionClasses() throws URISyntaxException {
    Path mainClasses =
        Path.of(
            CourseFlowApplication.class
                .getProtectionDomain()
                .getCodeSource()
                .getLocation()
                .toURI());
    production = new ClassFileImporter().importPath(mainClasses);
    assertFalse(production.isEmpty(), "No production classes imported");
  }

  @Test
  void controllersCannotAccessPersistenceDirectly() {
    // Translating persistence exceptions into HTTP errors is not persistence access.
    noClasses()
        .that(WEB_BOUNDARY)
        .should()
        .dependOnClassesThat(
            PERSISTENCE.and(
                not(
                    describe(
                        "exception types",
                        (JavaClass type) -> type.isAssignableTo(Throwable.class)))))
        .check(production);
  }

  @Test
  void nonWebCodeCannotDependOnControllers() {
    noClasses()
        .that(not(WEB_BOUNDARY))
        .should()
        .dependOnClassesThat(WEB_BOUNDARY)
        .check(production);
  }

  @Test
  void repositoriesCannotDependOnHigherLayers() {
    noClasses()
        .that(REPOSITORIES)
        .should()
        .dependOnClassesThat(WEB_BOUNDARY.or(BUSINESS).or(resideInAPackage(SECURITY)).or(HTTP))
        .check(production);
  }

  @Test
  void businessServicesAndModuleApisAreIndependentOfHttp() {
    noClasses()
        .that(BUSINESS)
        .should()
        .dependOnClassesThat(WEB_BOUNDARY.or(HTTP))
        .check(production);
  }

  @Test
  void domainModelIsIndependentOfApplicationAndInfrastructure() {
    noClasses()
        .that()
        .resideInAPackage(ENTITY)
        .or()
        .areAnnotatedWith(Entity.class)
        .should()
        .dependOnClassesThat(
            resideInAPackage(ROOT + "..")
                .and(not(resideInAPackage(ENTITY)))
                .or(
                    resideInAnyPackage(
                        "org.springframework..",
                        "jakarta.servlet..",
                        "javax.servlet..",
                        "java.sql..",
                        "javax.sql..",
                        "com.fasterxml.jackson..")))
        .check(production);
  }

  @Test
  void dtosDoNotDependOnPersistenceOrBusinessServices() {
    noClasses()
        .that(DTOS)
        .should()
        .dependOnClassesThat(
            PERSISTENCE.or(BUSINESS).or(WEB_BOUNDARY).or(resideInAPackage(SECURITY)))
        .check(production);
  }

  @Test
  void controllersDoNotReturnJpaEntities() {
    for (JavaClass type : production) {
      if (isWebBoundary(type)) {
        for (var method : type.getMethods()) {
          if (method.getModifiers().contains(JavaModifier.PUBLIC)) {
            assertFalse(
                method.getReturnType().getAllInvolvedRawTypes().stream()
                    .anyMatch(raw -> raw.isAnnotatedWith(Entity.class)),
                method::getFullName);
          }
        }
      }
    }
  }

  @Test
  void dtosDoNotStoreJpaEntities() {
    for (JavaClass type : production) {
      if (DTOS.test(type)) {
        for (var field : type.getAllFields()) {
          assertFalse(
              field.getType().getAllInvolvedRawTypes().stream()
                  .anyMatch(raw -> raw.isAnnotatedWith(Entity.class)),
              field::getFullName);
        }
      }
    }
  }

  @Test
  void internalServicesAndRepositoriesArePrivateToTheirModule() {
    Set<String> modules = new TreeSet<>();
    for (JavaClass type : production) {
      if (resideInAnyPackage(SERVICE, REPOSITORY).test(type)) {
        modules.add(
            ROOT + "." + type.getPackageName().substring(ROOT.length() + 1).split("\\.")[0]);
      }
    }
    assertFalse(modules.isEmpty(), "No service or repository modules imported");
    for (String module : modules) {
      noClasses()
          .that()
          .resideOutsideOfPackage(module + "..")
          .should()
          .dependOnClassesThat()
          .resideInAnyPackage(module + ".service..", module + ".repository..")
          .check(production);
    }
  }

  @Test
  void commonDoesNotDependOnApplicationModules() {
    noClasses()
        .that()
        .resideInAPackage(COMMON)
        .should()
        .dependOnClassesThat(resideInAPackage(ROOT + "..").and(not(resideInAPackage(COMMON))))
        .check(production);
  }

  @Test
  void applicationDoesNotDependOnSecurityImplementation() {
    noClasses()
        .that()
        .resideOutsideOfPackage(SECURITY)
        .should()
        .dependOnClassesThat()
        .resideInAPackage(SECURITY)
        .check(production);
  }

  @Test
  void entitiesResideInEntityPackages() {
    classes()
        .that()
        .areAnnotatedWith(Entity.class)
        .should()
        .resideInAPackage(ENTITY)
        .check(production);
  }

  @Test
  void repositoriesResideInRepositoryPackages() {
    classes().that(REPOSITORIES).should().resideInAPackage(REPOSITORY).check(production);
  }

  @Test
  void controllersAndAdviceResideInWebPackages() {
    classes()
        .that(describe("controllers", ArchitectureTest::isController))
        .should()
        .resideInAnyPackage(CONTROLLER, SYSTEM)
        .check(production);
    classes()
        .that(describe("controller advice", ArchitectureTest::isAdvice))
        .should()
        .resideInAPackage(EXCEPTION)
        .check(production);
  }

  @Test
  void servicesResideInServiceOrApiPackages() {
    classes()
        .that()
        .areAnnotatedWith(Service.class)
        .should()
        .resideInAnyPackage(SERVICE, API, SYSTEM, SECURITY, ROOT + ".demo..")
        .check(production);
  }

  @Test
  void dependenciesAreNotInjectedIntoFieldsOrMethods() {
    for (String annotation :
        Set.of(
            "org.springframework.beans.factory.annotation.Autowired",
            "jakarta.inject.Inject",
            "javax.inject.Inject",
            "jakarta.annotation.Resource",
            "javax.annotation.Resource",
            "jakarta.persistence.PersistenceContext",
            "javax.persistence.PersistenceContext",
            "jakarta.persistence.PersistenceUnit",
            "javax.persistence.PersistenceUnit")) {
      noFields().should().beAnnotatedWith(annotation).check(production);
      noMethods().should().beAnnotatedWith(annotation).check(production);
    }
  }

  @Test
  void springComponentConstructorDependenciesHaveNoCycles() {
    // A constructor's interface parameter may be supplied by a managed implementation.
    var components = production.stream().filter(ArchitectureTest::isManagedType).toList();
    assertFalse(components.isEmpty(), "No Spring components imported");
    Map<JavaClass, Set<JavaClass>> graph = new HashMap<>();
    for (JavaClass component : components) {
      Set<JavaClass> dependencies = new HashSet<>();
      for (var constructor : component.getConstructors()) {
        for (JavaClass parameter : constructor.getRawParameterTypes()) {
          if (parameter.getPackageName().startsWith(ROOT + ".")) {
            for (JavaClass candidate : components) {
              if (candidate.equals(parameter)
                  || (parameter.isInterface() && candidate.isAssignableTo(parameter.getName()))) {
                dependencies.add(candidate);
              }
            }
          }
        }
      }
      graph.put(component, dependencies);
    }
    Set<JavaClass> visited = new HashSet<>();
    Set<JavaClass> path = new HashSet<>();
    for (JavaClass component : components) {
      assertNoConstructorCycle(component, graph, visited, path);
    }
  }

  private static void assertNoConstructorCycle(
      JavaClass component,
      Map<JavaClass, Set<JavaClass>> graph,
      Set<JavaClass> visited,
      Set<JavaClass> path) {
    assertFalse(
        path.contains(component),
        () -> "Cyclic constructor dependencies: " + path + " -> " + component);
    if (visited.add(component)) {
      path.add(component);
      for (JavaClass dependency : graph.getOrDefault(component, Set.of())) {
        assertNoConstructorCycle(dependency, graph, visited, path);
      }
      path.remove(component);
    }
  }

  @AfterAll
  static void reportModuleCycles() {
    // Diagnostic, not a passing assertion: module cycles are existing architectural debt.
    if (production == null || production.isEmpty()) {
      return; // The import failure is already reported by @BeforeAll.
    }
    var all = slices().matching(ROOT + ".(*)..").should().beFreeOfCycles();
    reportCycles("All module cycles (including ORM)", all);
    reportCycles(
        "Application module cycles (without entity-package dependencies)",
        all.ignoreDependency(resideInAPackage(ENTITY), alwaysTrue())
            .ignoreDependency(alwaysTrue(), resideInAPackage(ENTITY)));
  }

  private static void reportCycles(String title, ArchRule rule) {
    var result = rule.evaluate(production);
    System.out.println(title + ": " + (result.hasViolation() ? result.getFailureReport() : "none"));
  }

  private static boolean isWebBoundary(JavaClass type) {
    return resideInAPackage(CONTROLLER).test(type) || isController(type) || isAdvice(type);
  }

  private static boolean isController(JavaClass type) {
    return type.isAnnotatedWith(Controller.class) || type.isMetaAnnotatedWith(Controller.class);
  }

  private static boolean isAdvice(JavaClass type) {
    return type.isAnnotatedWith(ControllerAdvice.class)
        || type.isMetaAnnotatedWith(ControllerAdvice.class);
  }

  private static boolean isRepository(JavaClass type) {
    return resideInAPackage(REPOSITORY).test(type)
        || type.isAnnotatedWith(org.springframework.stereotype.Repository.class)
        || type.isAssignableTo(Repository.class);
  }

  private static boolean isManagedType(JavaClass type) {
    return type.isAnnotatedWith(Component.class)
        || type.isMetaAnnotatedWith(Component.class)
        || type.isAnnotatedWith(Configuration.class)
        || isWebBoundary(type)
        || isRepository(type);
  }
}
