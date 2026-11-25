package org.courseflow.architecture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

/** Fixed source-level baseline, complementary to the bytecode rules in ArchitectureTest. */
class SourceDependencyTest {
  private static final Pattern REFERENCE =
      Pattern.compile("\\borg\\.courseflow\\.(?:[a-z][A-Za-z0-9_]*\\.)+[A-Z][A-Za-z0-9_]*");
  private static final String ROOT = "org.courseflow.";

  @Test
  void explicitCrossModuleTypeReferencesCannotGrow() throws Exception {
    Path source = Path.of("src", "main", "java", "org", "courseflow");
    assertTrue(Files.isDirectory(source), "Production source directory missing: " + source);
    Set<String> actual = new TreeSet<>();
    try (var paths = Files.walk(source)) {
      for (Path file : paths.filter(p -> p.toString().endsWith(".java")).toList()) {
        String name = source.relativize(file).toString().replace('\\', '/').replace('/', '.');
        String text = Files.readString(file);
        assertFalse(Pattern.compile("(?m)^\\s*import\\s+(?:static\\s+)?org\\.courseflow\\.[^;]*\\.\\*\\s*;")
            .matcher(text).find(), "Wildcard project import: " + file);
        actual.addAll(references(ROOT + name.substring(0, name.length() - 5), text));
      }
    }
    assertFalse(actual.isEmpty(), "No inter-module source dependencies found");
    Set<String> baseline = new TreeSet<>();
    try (var stream = getClass().getResourceAsStream("/architecture/reviewed-source-dependencies.txt")) {
      assertNotNull(stream, "Reviewed source dependency baseline missing");
      baseline.addAll(new String(stream.readAllBytes(), StandardCharsets.UTF_8).lines().toList());
    }
    assertFalse(baseline.isEmpty(), "Empty dependency baseline");
    Set<String> added = new TreeSet<>(actual);
    added.removeAll(baseline);
    Set<String> removed = new TreeSet<>(baseline);
    removed.removeAll(actual);
    System.out.println("Explicit cross-module type references: " + actual.size());
    assertTrue(added.isEmpty(), () -> "New cross-module source dependencies: " + added);
    assertTrue(removed.isEmpty(), () -> "Remove obsolete entries from reviewed baseline: " + removed);
  }

  @Test
  void detectsNewTypeWithinExistingModuleDirection() {
    String origin = "org.courseflow.catalog.service.Example";
    Set<String> baseline = references(origin, "import org.courseflow.learning.api.First;");
    Set<String> changed = references(origin, "import org.courseflow.learning.api.First; import org.courseflow.learning.api.Second;");
    changed.removeAll(baseline);
    assertEquals(Set.of(origin + " -> org.courseflow.learning.api.Second"), changed);
  }

  @Test
  void detectsQualifiedReferencesWithoutImportAndIgnoresOwnModule() {
    String origin = "org.courseflow.catalog.service.Example";
    assertEquals(
        Set.of(origin + " -> org.courseflow.learning.api.First"),
        references(origin, "org.courseflow.learning.api.First.call(); org.courseflow.catalog.api.Own.call();"));
  }

  private static Set<String> references(String origin, String source) {
    Set<String> edges = new TreeSet<>();
    var matcher = REFERENCE.matcher(source);
    while (matcher.find()) {
      String target = matcher.group();
      if (!module(origin).equals(module(target))) {
        edges.add(origin + " -> " + target);
      }
    }
    return edges;
  }

  private static String module(String type) {
    return type.substring(ROOT.length()).split("\\.", 2)[0];
  }
}
